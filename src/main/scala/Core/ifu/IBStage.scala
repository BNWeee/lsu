package Core.ifu
import Core.utils.UIntToMask
import Core.{Config, CoreBundle}
import chisel3._
import chisel3.util._

class IBStage extends Module with Config {
  val io = IO(new IBStageIO)
  val chgflow_pc = RegInit(0.U(VAddrBits.W))

}
