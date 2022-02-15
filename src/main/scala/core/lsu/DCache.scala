package core.lsu

import chisel3._
import chisel3.util._

class CacheAddrBundle extends CoreBundle {
   val tag        = UInt((VAddrBits - IndexBits - OffsetBits).W)
   val index      = UInt(IndexBits.W)
   val Offset     = UInt((OffsetBits-3).W)
   val doubleword = UInt(3.W)
}

class RefillReq extends CoreBundle {
  val valid    = Output(Bool())
  val ready    = Input(Bool())
  val paddr    = Output(UInt(PAddrBits.W))
  //1 cycle delay
  val dirty    = Output(Bool())
  val wb_paddr = Output(UInt(PAddrBits.W))
  val wb_data  = Output(UInt(CacheLineBits.W))
}

class RefillResp extends CoreBundle {
  val data = UInt(CacheLineBits.W)
}

class DCacheIO extends CoreBundle {
  val load  = new Load_Cache
  val store = new Store_Cache
  val refill = new Bundle{
    val req = new RefillReq
    val resp = Flipped(Valid(new RefillResp))
  }
}

class DCache extends Module with Config {
  val io = IO(new DCacheIO)

  val valid         = Seq.fill(DCacheWays)(RegInit(VecInit(Seq.fill(DCacheSets)(false.B))))
  val dirty         = Seq.fill(DCacheWays)(RegInit(VecInit(Seq.fill(DCacheSets)(false.B))))
  val need_refill   = Seq.fill(DCacheWays)(RegInit(VecInit(Seq.fill(DCacheSets)(false.B))))
  val tagArray      = Seq.fill(DCacheWays)(RegInit(VecInit(Seq.fill(DCacheSets)(0.U(DCacheTagBits.W)))))
  val dataArray     = Seq.fill(DCacheWays)(Module(new DCacheDataArray))

  val refill_way_sel = RegInit(VecInit(Seq.fill(DCacheSets)(false.B)))

  //1.load
  val load_hit_check = WireInit(VecInit(Seq.fill(DCacheWays)(false.B)))
  val load_idx = io.load.vaddr(IndexBits+OffsetBits-1,OffsetBits)
  for(i <- 0 until DCacheWays) {
    load_hit_check(i) := io.load.paddr.valid && valid(i)(load_idx) && tagArray(i)(load_idx) === io.load.paddr.bits(PAddrBits-1,12)
  }

  for(i <- 0 until DCacheWays){
    dataArray(i).io.read(0).addr := load_idx
  }

  val load_valid = RegNext(io.load.paddr.valid)
  val load_vaddr = RegNext(io.load.vaddr.asTypeOf(new CacheAddrBundle))
  val load_hit   = RegNext(load_hit_check)

  val load_data = Mux(load_hit(0), dataArray(0).io.read(0).data, dataArray(1).io.read(0).data) >> (load_vaddr.Offset << 6.U)

  io.load.res.valid := load_valid
  io.load.res.bits  := load_data
  io.load.miss := !load_hit.asUInt.orR

  //2.store
  val store_hit_check = WireInit(VecInit(Seq.fill(DCacheWays)(false.B)))
  val store_idx = io.store.vaddr(IndexBits+OffsetBits-1,OffsetBits)
  for(i <- 0 until DCacheWays) {
    store_hit_check(i) := io.store.valid && valid(i)(store_idx) && tagArray(i)(store_idx) === io.store.paddr(PAddrBits-1,12)
  }
  io.store.miss := !store_hit_check.asUInt.orR

  for(i <- 0 until DCacheWays){
    dataArray(i).io.read(1).addr := store_idx
  }

  val store_valid = RegNext(io.store.valid)
  val store_vaddr = RegNext(io.store.vaddr.asTypeOf(new CacheAddrBundle))
  val store_data  = RegNext(io.store.data)
  val store_mask  = RegNext(io.store.mask)
  val store_hit   = RegNext(store_hit_check)

  for(i <- 0 until DCacheWays){
    dataArray(i).io.write(0).wen  := store_valid && store_hit(i)
    dataArray(i).io.write(0).addr := store_vaddr.index
    dataArray(i).io.write(0).data := store_data << (store_vaddr.Offset << 6.U)
    dataArray(i).io.write(0).mask := store_mask << (store_vaddr.Offset << 3.U)
    when(store_valid && store_hit(i)){
      dirty(i)(store_vaddr.index) := true.B
    }
  }

  //3.refill
  val refill_way_reg = RegInit(false.B)
  val refill_idx_reg = RegInit(0.U(IndexBits.W))
  val refill_way_wire = WireInit(false.B)
  val refill_idx_wire = WireInit(0.U(IndexBits.W))

  val load_miss  = io.load.paddr.valid && !load_hit_check.asUInt.orR
  val store_miss = io.store.valid && !store_hit_check.asUInt.orR
  when((load_miss || store_miss) && io.refill.req.ready){
    refill_way_reg := Mux(load_miss, refill_way_sel(load_idx), refill_way_sel(store_idx))
    refill_idx_reg := Mux(load_miss, load_idx, store_idx)

    refill_way_wire := Mux(load_miss, refill_way_sel(load_idx), refill_way_sel(store_idx))
    refill_idx_wire := Mux(load_miss, load_idx, store_idx)
    for(i <- 0 until DCacheWays){
      when(refill_way_wire === i.U){
        need_refill(i)(refill_idx_wire) := true.B
        valid(i)(refill_idx_wire)       := false.B
        refill_way_sel(refill_idx_wire) := refill_way_sel(refill_idx_wire) + 1.U
        tagArray(i)(refill_idx_wire)    := (Mux(load_miss, io.load.paddr.bits, io.store.paddr))(PAddrBits-1,12)
      }
    }

    io.refill.req.valid := true.B
  }.otherwise{
    io.refill.req.valid := false.B
  }
  io.refill.req.paddr := Mux(load_miss, io.load.paddr.bits, io.store.paddr)

  //1 cycle delay
  val wb_dirty = WireInit(false.B)
  val wb_paddr = WireInit(0.U(PAddrBits.W))
  val wb_data  = WireInit(0.U(CacheLineBits.W))

  for(i <- 0 until DCacheWays){
    when(refill_way_reg === i.U){
      wb_dirty := dirty(i)(refill_idx_reg)
      wb_paddr := Cat(tagArray(i)(refill_idx_reg), refill_idx_reg(12-OffsetBits-1,0), 0.U(OffsetBits.W))
      wb_data  := Mux(RegNext(load_miss), dataArray(i).io.read(0).data, dataArray(i).io.read(1).data)
    }
  }
  io.refill.req.dirty := wb_dirty
  io.refill.req.wb_paddr := wb_paddr
  io.refill.req.wb_data := wb_data
  //refill wb
  for(i <- 0 until DCacheWays) {
    when(io.refill.resp.valid && refill_way_reg === i.U) {
      valid(i)(refill_idx_reg) := true.B
      need_refill(i)(refill_idx_reg) := false.B
      dirty(i)(refill_idx_reg) := false.B
      dataArray(i).io.write(1).wen := true.B
    }.otherwise{
      dataArray(i).io.write(1).wen := false.B
    }
    dataArray(i).io.write(1).addr := refill_idx_reg
    dataArray(i).io.write(1).data := io.refill.resp.bits.data
    dataArray(i).io.write(1).mask := VecInit(Seq.fill(CacheLineSize)(true.B)).asUInt()
  }

}
