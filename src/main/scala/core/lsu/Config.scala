package core.lsu

import chisel3._

abstract class CoreBundle extends Bundle with Config {}

trait Config {
  val XLEN = 64
  val VAddrBits = 39
  val PAddrBits = 40
  val LSQueueSize = 32
  val StoreBufferSize = 16
  val RobSize = 128
  val NRPhyRegs = 64
}

object Config extends Config{}

object LSUOpType {
  def lb   = "b0000000".U
  def lh   = "b0000001".U
  def lw   = "b0000010".U
  def ld   = "b0000011".U
  def lbu  = "b0000100".U
  def lhu  = "b0000101".U
  def lwu  = "b0000110".U
  def sb   = "b0001000".U
  def sh   = "b0001001".U
  def sw   = "b0001010".U
  def sd   = "b0001011".U

  def lr   = "b0100000".U
  def sc   = "b0100001".U

  def isStore(func: UInt): Bool = func(3)
  def isLoad(func: UInt): Bool = !isStore(func)
  def isLR(func: UInt): Bool = func === lr
  def isSC(func: UInt): Bool = func === sc
  def needMemRead(func: UInt): Bool  = isLoad(func) || isLR(func)
  def needMemWrite(func: UInt): Bool = isStore(func) || isSC(func)
}

object FuncOpType {
  def width = 7.W
  def uwidth = UInt(width)
}