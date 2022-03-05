package Core.ifu

import chisel3._
import chisel3.util._
import chisel3.experimental._
import Core.utils._

trait SramConfig {
  def Bits = 128;
  def WordDepth = 64;
  def AddrWidth = 6;
  def WenWidth = 128;
}

class ct_spsram_2048x32 extends ExtModule with HasExtModuleResource with SramConfig {
  val A = IO(Input(UInt(AddrWidth.W)))
  val CEN = IO(Input(Bool()))
  val CLK = IO(Input(Clock()))
  val D = IO(Input(UInt(Bits.W)))
  val GWEN = IO(Input(Bool()))
  val Q = IO(Output(UInt(Bits.W)))
  val WEN = IO(Input(UInt(WenWidth.W)))


  addResource("/vsrc/ct_spsram_2048x32_split.v")
}



class SramIO extends Bundle with SramConfig {
  val rData: UInt = OutUInt(Bits)

  val en: Bool = InBool()
  val idx: UInt = InUInt(AddrWidth)
  val wen: Bool = InBool()
  val wMask: UInt = InUInt(WenWidth)
  val wData: UInt = InUInt(Bits)
}

class sram_c910 extends Module {
  val io = IO(new SramIO)
  val bank0: ct_spsram_2048x32 = Module(new ct_spsram_2048x32)
  val bank1: ct_spsram_2048x32 = Module(new ct_spsram_2048x32)
  val bank2: ct_spsram_2048x32 = Module(new ct_spsram_2048x32)
  val bank3: ct_spsram_2048x32 = Module(new ct_spsram_2048x32)
  io.rData  := Cat(bank0.Q,bank1.Q,bank2.Q,bank3.Q)
  bank0.CLK  := clock
  bank0.CEN  := !io.en
  bank0.A    := io.idx
  bank0.GWEN  := !io.wen
  bank0.WEN := ~io.wMask(31,0)
  bank0.D    := io.wData(31,0)
  bank1.CLK  := clock
  bank1.CEN  := !io.en
  bank1.A    := io.idx
  bank1.GWEN  := !io.wen
  bank1.WEN := ~io.wMask(63,32)
  bank1.D    := io.wData(63,32)
  bank2.CLK  := clock
  bank2.CEN  := !io.en
  bank2.A    := io.idx
  bank2.GWEN  := !io.wen
  bank2.WEN := ~io.wMask(95,64)
  bank2.D    := io.wData(95,64)
  bank3.CLK  := clock
  bank3.CEN  := !io.en
  bank3.A    := io.idx
  bank3.GWEN  := !io.wen
  bank3.WEN := ~io.wMask(127,96)
  bank3.D    := io.wData(127,96)
}

class SramIO_2048x32 extends Bundle with SramConfig {
  val rData: UInt = OutUInt(32)

  val en: Bool = InBool()
  val idx: UInt = InUInt(AddrWidth)
  val wen: Bool = InBool()
  val wMask: UInt = InUInt(32)
  val wData: UInt = InUInt(32)
}
class sram_2048x32 extends Module {
  val io = IO(new SramIO_2048x32)
  val sram_2048x32: ct_spsram_2048x32 = Module(new ct_spsram_2048x32)
  io.rData  := sram_2048x32.Q
  sram_2048x32.CLK  := clock
  sram_2048x32.CEN  := !io.en
  sram_2048x32.A    := io.idx
  sram_2048x32.WEN  := !io.wen
  sram_2048x32.GWEN := ~io.wMask
  sram_2048x32.D    := io.wData
}