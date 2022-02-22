package Core.lsu
import Core.Config
import Core.utils.RAMHelper
import chisel3._
import chisel3.util._

class DCacheRefill extends Module with Config {
  val io = IO(new Bundle{
    val req = Flipped(new RefillReq)
    val resp = Valid(new RefillResp)
  })
  val s_idle :: s_req :: s_refill :: Nil = Enum(3)
  val state = RegInit(s_idle)

  val rf_paddr = RegInit(0.U(PAddrBits.W))
  val rf_data  = RegInit(VecInit(Seq.fill(CacheLineBits/DCRefillBits)(0.U(DCRefillBits.W))))
  val wb_dirty = RegInit(false.B)
  val wb_paddr = RegInit(0.U(PAddrBits.W))
  val wb_data  = RegInit(VecInit(Seq.fill(CacheLineBits/DCRefillBits)(0.U(DCRefillBits.W))))

  val read_cnt  = RegInit(0.U((RefillCnt+1).W))
  val write_cnt = RegInit(0.U((RefillCnt+1).W))

  io.req.ready := state === s_idle
  when(state === s_idle && io.req.valid){
    rf_paddr := io.req.paddr
  }
  when(state === s_req && RegNext(io.req.valid)){
    wb_dirty  := io.req.dirty
    wb_paddr  := io.req.wb_paddr
    wb_data   := io.req.wb_data.asTypeOf(wb_data)
    read_cnt  := 0.U
    write_cnt := 0.U
  }

  val read_addr  = WireInit(0.U(PAddrBits.W))
  val write_addr = WireInit(0.U(PAddrBits.W))
  read_addr  := Cat(rf_paddr(PAddrBits-1,OffsetBits), 0.U(OffsetBits)) + (read_cnt << 3.U)
  write_addr := Cat(wb_paddr(PAddrBits-1,OffsetBits), 0.U(OffsetBits)) + (write_cnt << 3.U)

  val read_ram = Module(new RAMHelper)
  read_ram.io.clk  := clock
  read_ram.io.en   := state === s_refill
  read_ram.io.rIdx := (read_addr - PcStart.U) >> 3
  read_ram.io.wIdx := DontCare
  read_ram.io.wen  := false.B
  read_ram.io.wdata := DontCare
  read_ram.io.wmask := DontCare
  rf_data(read_cnt) :=  read_ram.io.rdata

  val write_ram = Module(new RAMHelper)
  write_ram.io.clk  := clock
  write_ram.io.en   := state === s_refill
  write_ram.io.rIdx := DontCare
  write_ram.io.wIdx := (write_addr - PcStart.U) >> 3
  write_ram.io.wen  := (write_cnt < RefillCnt.U) && state === s_refill
  write_ram.io.wdata := wb_data(write_cnt)
  write_ram.io.wmask := VecInit(Seq.fill(DCRefillBits)(true.B)).asUInt()

  when(state === s_refill && read_cnt < RefillCnt.U){
    read_cnt := read_cnt + 1.U
  }
  when(state === s_refill && write_cnt < RefillCnt.U){
    write_cnt := write_cnt + 1.U
  }

  when(state === s_idle && RegNext(state) === s_refill){
    io.resp.valid := true.B
  }.otherwise{
    io.resp.valid := false.B
  }
  io.resp.bits.data := rf_data


  //-------------------------------------状态机------------------------------------------------
  switch(state) {
    is(s_idle){
      when(io.req.valid){
        state := s_req
      }
    }

    is(s_req){
      state := s_refill
    }

    is(s_refill){
      when(read_cnt === RefillCnt.U && write_cnt === RefillCnt.U){
        state := s_idle
      }
    }
  }
}
