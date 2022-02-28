package Core.ifu
import Core.{Config, CoreBundle}
import chisel3._
import chisel3.util._
class RASIO extends CoreBundle {

  val cp0_ifu_ras_en          = Input(Bool())
  val ibdp_ras_push_pc        = Input(UInt(VAddrBits.W))
  val rtu_ifu_flush           = Input(Bool())
  val rtu_ifu_mispred         = Input(Bool())
  val rtu_ifu_pcall           = Input(Bool())
  val rtu_ifu_preturn         = Input(Bool())
  val ibctrl_ras_pcall_vld    = Input(Bool())
  val ibctrl_ras_preturn_vld  = Input(Bool())

  val ras_ipdp_pc             = Output(UInt(VAddrBits.W))
  val ras_l0_btb_pc           = Output(UInt(VAddrBits.W))
  val ras_l0_btb_push_pc      = Output(UInt(VAddrBits.W))
}
class RAS extends Module with Config {
  val io = IO(new RASIO)
  val ras = Mem(ifu_ras, UInt(VAddrBits.W))
  //ras fifo
  val ras_push = Wire(Bool())
  val ras_pop  = Wire(Bool())
  val ras_return = Wire(Bool())
  val ras_push_pc = Wire(UInt(VAddrBits.W))
  val ras_pc_out = Wire(UInt(VAddrBits.W))
  ras_push := io.ibctrl_ras_pcall_vld
  ras_push := io.ibctrl_ras_preturn_vld
  ras_push_pc(VAddrBits-1,0) := io.ibdp_ras_push_pc(VAddrBits-1,0)

  //rtu fifo
  val rtu_ifu_pcall = Wire(Bool())
  val rtu_ifu_preturn = Wire(Bool())
  rtu_ifu_pcall := io.rtu_ifu_pcall
  rtu_ifu_preturn := io.rtu_ifu_preturn

  val rtu_ptr     = RegInit(0.U(5.W))
  val rtu_ptr_pre = RegInit(0.U(5.W))
  val ras_ptr     = RegInit(0.U(5.W))
  val ras_ptr_pre = RegInit(0.U(5.W))
  val status_ptr  = RegInit(0.U(5.W))


  val rtu_ras_empty = (rtu_ptr === status_ptr)
  when(rtu_ifu_preturn && rtu_ifu_pcall) {
    rtu_ptr_pre := rtu_ptr
  }.elsewhen(rtu_ifu_pcall) {
    when(rtu_ptr(3,0) === (ifu_ras-1).U) {
      rtu_ptr_pre := Cat(!rtu_ptr(4),"b0000".U)  //rtu_ras stack overflow
    }.otherwise {
      rtu_ptr_pre := rtu_ptr + 1.U
    }
  }.elsewhen(rtu_ifu_preturn && !rtu_ras_empty) {
    when(rtu_ptr(3,0) === 0.U) {
      rtu_ptr_pre := Cat(!rtu_ptr(4),"b1011".U)
    }.otherwise {
      rtu_ptr_pre := rtu_ptr - 1.U
    }
  }.otherwise {
    rtu_ptr_pre := rtu_ptr
  }
  rtu_ptr := Mux(io.cp0_ifu_ras_en,rtu_ptr_pre,rtu_ptr)

  //ras ptr
  val rtu_need = io.rtu_ifu_mispred || io.rtu_ifu_flush
  val ras_empty = ras_ptr === status_ptr
  val ras_full  = ras_ptr === Cat(!status_ptr(4),status_ptr(3,0))
  when(rtu_need) {
    ras_ptr_pre := rtu_ptr_pre
  }.elsewhen(ras_push && ras_return) {
    ras_ptr_pre := rtu_ptr
  }.elsewhen(ras_push) {
    when(ras_ptr(3,0) === (ifu_ras-1).U) {
      ras_ptr_pre := Cat(!ras_ptr(4), "b0000".U)
    }.otherwise {
      ras_ptr_pre := ras_ptr + 1.U
    }
  }.elsewhen(ras_pop && !ras_empty) {
    when(ras_ptr(3,0) === "b0000".U) {
      ras_ptr_pre := Cat(!ras_ptr(4), "b1011".U)
    }.otherwise {
      ras_ptr_pre := ras_ptr - 1.U
    }
  }.otherwise {
    ras_ptr_pre := ras_ptr
  }
  ras_ptr := Mux(io.cp0_ifu_ras_en,ras_ptr_pre,ras_ptr)

  ras_pc_out            := ras.read(rtu_ptr)
  when(ras_push){
    ras.write(rtu_ptr,ras_push_pc)
  }
  io.ras_ipdp_pc        := Mux(io.ibctrl_ras_inst_pcall,io.ibdp_ras_push_pc,ras_pc_out)
  io.ras_l0_btb_push_pc := io.ibdp_ras_push_pc
  io.ras_ipdp_pc        := ras_pc_out

}
