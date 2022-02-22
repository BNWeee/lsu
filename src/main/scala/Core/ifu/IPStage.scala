package Core.ifu
import Core.utils.UIntToMask
import Core.{Config, CoreBundle}
import chisel3._
import chisel3.util._

class IPStage extends Module with Config {
  val io = IO(new IPStageIO)

  val ip_data_valid = !io.ip_flush && io.icache_resp.valid

  val pc_mask = UIntToMask.rightmask(io.pc(3,1), 9)(8,1)

  val inst_32 = Wire(Vec(8,Bool()))
  for(i <- 0 until 8){
    inst_32(i) := io.icache_resp.bits.inst_data(i) === "0b11".U
  }

  //last half inst
  val h0_valid     = RegInit(false.B)
  val h0_data      = RegInit(0.U(16.W))
  val h0_predecode = RegInit(0.U(4.W))
  val h0_br    = h0_valid && h0_predecode(2)
  val h0_ab_br = h0_valid && h0_predecode(3)
  h0_data      := io.icache_resp.bits.inst_data(7)
  h0_predecode := io.icache_resp.bits.predecode(7)

  //predecode
  val bry0  = Wire(Vec(8,Bool()))
  val bry1  = Wire(Vec(8,Bool()))
  val br    = Wire(Vec(8,Bool()))
  val ab_br = Wire(Vec(8,Bool()))

  for(i <- 0 until 8) {
    bry0(i)  := io.icache_resp.bits.predecode(i)(0)
    bry1(i)  := io.icache_resp.bits.predecode(i)(1)
    br(i)    := io.icache_resp.bits.predecode(i)(2)
    ab_br(i) := io.icache_resp.bits.predecode(i)(3)
  }

  //bry1_hit是数学问题，想了很久，终于明白了。。。
  val bry1_hit = !h0_valid && bry1(io.pc(3,1))
  val bry = Mux(bry1_hit, bry1, bry0)

  //h0_valid update
  when(!io.ip_redirect.valid && !io.ubtb_resp.valid && bry(7) && inst_32(7)){
    h0_valid := true.B
  }.otherwise{
    h0_valid := false.B
  }

//==========================================================
//                   BHT Information
//==========================================================
//BHT Result Get
  val bht_pre_array  = Mux(io.bth_resp.pre_sel(1), io.bth_resp.pre_taken, io.bth_resp.pre_ntaken)
  val bht_pre_result = bht_pre_array(io.bth_resp.pre_offset)

  val br_taken = Wire(Vec(8,Bool()))
  val br_ntake = Wire(Vec(8,Bool()))

  br_taken(0) := (br(0) && pc_mask(0) && bry(0)) || h0_br
  br_ntake(0) := (ab_br(0) && pc_mask(0) && bry(0))

  br_taken(7) := (br(7) && pc_mask(7) && bry(7)) && !inst_32(7)
  br_ntake(7) := (ab_br(7) && pc_mask(7) && bry(7)) && !inst_32(7)

  for(i <- 1 until 7){
    br_taken(i) := br(i) && pc_mask(i) && bry(i)
    br_ntake(i) := ab_br(i) && pc_mask(i) && bry(i)
  }

  val br_mask = Mux(bht_pre_result(1), br_taken, br_ntake)

  //btb result
  val btb_sel = WireInit(Vec(4,Bool()))
  for(i <- 0 until 4){
    btb_sel(i) := br_mask(2*i+1) || br_mask(2*i)
  }

  val btb_target = PriorityMux(Seq(
    btb_sel(0) -> io.btb_resp(0).bits,
    btb_sel(1) -> io.btb_resp(1).bits,
    btb_sel(2) -> io.btb_resp(2).bits,
    btb_sel(3) -> io.btb_resp(3).bits
  ))

  val btb_valid = PriorityMux(Seq(
    btb_sel(0) -> io.btb_resp(0).valid,
    btb_sel(1) -> io.btb_resp(1).valid,
    btb_sel(2) -> io.btb_resp(2).valid,
    btb_sel(3) -> io.btb_resp(3).valid
  ))

  //pred info
  val btb_miss     = br_mask.asUInt.orR && !btb_valid
  val ubtb_miss    = br_mask.asUInt.orR && !io.ubtb_resp.valid
  val ubtb_mispred = br_mask.asUInt.orR && io.ubtb_resp.valid && (btb_target =/= io.ubtb_resp.bits.target_pc(20,1) && !io.ubtb_resp.bits.is_ret)

  io.ip_redirect.valid := ip_data_valid && btb_valid && (ubtb_miss || ubtb_mispred)
  io.ip_redirect.bits  := Cat(io.pc(VAddrBits-1,21), btb_target, 0.U(1.W))

  //ipdecode
  val ipdecode = Module(new IPDecode)
  ipdecode.io.half_inst := h0_data +: io.icache_resp.bits.inst_data
  ipdecode.io.icache_br := h0_br +: br

  //to IBStage
  io.out.valid := ip_data_valid
  io.out.bits.pc := io.pc
  io.out.bits.icache_resp := io.icache_resp.bits
  io.out.bits.decode_info := ipdecode.io.decode_info
  io.out.bits.bht_resp := io.bth_resp
  io.out.bits.btb_valid := btb_valid
  io.out.bits.btb_target := btb_target
  io.out.bits.btb_miss := btb_miss
  io.out.bits.btb_sel := btb_sel
  io.out.bits.ubtb_valid := io.ubtb_resp.valid
  io.out.bits.ubtb_resp := io.ubtb_resp.bits
  io.out.bits.ubtb_miss := ubtb_miss
  io.out.bits.ubtb_mispred := ubtb_mispred

}
