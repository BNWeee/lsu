//package Core.ifu
//import Core.{Config, CoreBundle}
//import chisel3._
//import chisel3.util._
//class ib_addrgen extends CoreBundle {
//  val ib_br_base = Input(UInt(VAddrBits.W))
//  val ib_br_offset = Input(UInt(21.W))
//  val ib_br_result = Input(UInt(VAddrBits.W))
//  val ib_btb_index_pc = Input(UInt(VAddrBits.W))
//  val ib_ubtb_hit  = Input(Bool())
//  val ib_ubtb_hit_entry = Input(UInt(16.W))
//}
//class AddrGenIO extends CoreBundle {
//  val in = new ib_addrgen
//  val btb_update = Output(new BTBUpdate)
//  val ubtb_update = Output(new uBTBUpdateData)
//
//}
//class AddrGen extends Module with Config {
//  val io = IO(new AddrGenIO)
//  val brach_pc = io.in.ib_br_base
//  val branch_offset = Cat((io.in.ib_br_offset(20)), io.in.ib_br_offset(20,1))
//  val branch_cal_result:UInt = brach_pc + branch_offset
//  val branch_pred_result:UInt = io.in.ib_br_result
//  val branch_mispred:Bool = branch_cal_result =/= branch_pred_result
//  val branch_index = io.in.ib_btb_index_pc(12,3)
//  val branch_tag = Cat(io.in.ib_btb_index_pc(19,13),io.in.ib_btb_index_pc(2,0))
//  val ubtb_hit = io.in.ib_ubtb_hit
//
//  io.btb_update.btb_tag := branch_tag
//  io.btb_update.btb_index := branch_index
//  io.btb_update.btb_data := branch_cal_result(19,0)
//
//  io.ubtb_update.entry_valid := branch_mispred && ubtb_hit  //when ubtb hit but btb mispredict,ubtb needs to update
//
//
//}
