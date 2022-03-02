package Core.ifu
import Core.utils.UIntToMask
import Core.{Config, CoreBundle}
import chisel3._
import chisel3.util._

class IBStage extends Module with Config {
  val io = IO(new IBStageIO)

  //check
  val ubtb_miss = io.ip2ib.bits.ubtb_miss
  val ubtb_valid = io.ip2ib.bits.ubtb_valid
  val ubtb_mispred = io.ip2ib.bits.ubtb_mispred
//  val ras_miss = ! io.ip2ib.bits.ubtb_resp.is_ret || ! io.ip2ib.bits.ubtb_valid && io.ip2ib.bits.decode_info
  val ras_mistaken = io.ip2ib.bits.ubtb_resp.is_ret && io.ip2ib.bits.ubtb_valid
  val ras_mispred  = io.ip2ib.bits.ubtb_resp.is_ret && ! io.ip2ib.bits.ubtb_valid
  io.btbmiss := io.ip2ib.bits.btb_miss
//  io.ubtb_update.entry_valid :=

  //push pc to ras
  io.ras_push_pc := io.ip2ib.bits.push_pc

  //addrgen
  io.ib2addrgen := io.ip_ib_addr

}
