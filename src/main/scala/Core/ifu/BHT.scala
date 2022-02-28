package Core.ifu
import Core.{Config, CoreBundle}
import chisel3._
import chisel3.util._
class rtu_bht extends CoreBundle {
  val rtu_ifu_retire0_condbr = Input(Bool())
  val rtu_ifu_retire1_condbr = Input(Bool())
  val rtu_ifu_retire2_condbr = Input(Bool())
  val rtu_ifu_retire0_condbr_taken = Input(Bool())
  val rtu_ifu_retire1_condbr_taken = Input(Bool())
  val rtu_ifu_retire2_condbr_taken = Input(Bool())
}
class bht_resp extends CoreBundle {

}
class BHTIO extends  CoreBundle {
  val rtu_bht = new rtu_bht
  val bht_resp = new bht_resp
  val pc = Input(UInt(VAddrBits.W))
  val ipctrl_bht_con_br_taken = Input(Bool())
  val ipctrl_bht_con_br_vld   = Input(Bool())
  val cur_condbr_taken = Input(UInt(1.W))
  val cur_pre_rst      = Input(UInt(2.W))
  val cur_sel_rst      = Input(UInt(2.W))
  val cur_pc           = Input(UInt(VAddrBits.W))
  val cur_ghr          = Input(UInt(ghr_size.W))
  val bht_ipdp_pre_array_data_ntaken = Output(UInt(pre_array_data_size.W))
  val bht_ipdp_pre_array_data_taken = Output(UInt(pre_array_data_size.W))
  val bht_ipdp_sel_array_result = Output(UInt(2.W))
  val bht_ipdp_vghr = Output(UInt(ghr_size.W))
  val rtu_ifu_flush          = Input(Bool())
  val bht_ghr       = Output(UInt(8.W))
  val rtu_ghr                = Output(UInt(8.W))
}
class BHT extends Module with Config {
  val io = IO(new BHTIO)
  val sel_array = RegInit(VecInit(Seq.fill(128)(VecInit(Seq.fill(8)(0.U(2.W))))))
  val pre_array = RegInit(VecInit(Seq.fill(1024)(VecInit(Seq.fill(2)(0.U(32.W))))))
  val vghr = RegInit(0.U(22.W))
  val rtu_ghr_reg = RegInit(0.U(22.W))
  val rtu_ghr_pre = RegInit(0.U(22.W))

  //read
  val sel_array_index = io.pc(9,3)
  val sel_array_data_index = io.pc(5,3)
  val sel_array_data_cur = sel_array(sel_array_index)(sel_array_data_index)

  val pre_array_rd_index = Cat(vghr(12,9),vghr(8,3)^vghr(20,15))
  val pre_array_data_cur = pre_array(pre_array_rd_index)
  val pre_taken_data = pre_array_data_cur(0)
  val pre_ntaken_data = pre_array_data_cur(1)


  io.bht_ipdp_pre_array_data_taken := pre_taken_data
  io.bht_ipdp_pre_array_data_ntaken := pre_ntaken_data
  io.bht_ipdp_sel_array_result := sel_array_data_cur
  io.bht_ipdp_vghr := vghr

  //ghr update
  val rtu_con_br_vld = io.rtu_bht.rtu_ifu_retire0_condbr || io.rtu_bht.rtu_ifu_retire1_condbr || io.rtu_bht.rtu_ifu_retire2_condbr
  when(rtu_con_br_vld) {
    rtu_ghr_reg := rtu_ghr_pre
  }.otherwise {
    rtu_ghr_reg := rtu_ghr_reg
  }
  val rtu_condbr_cnt = Cat(io.rtu_bht.rtu_ifu_retire0_condbr,io.rtu_bht.rtu_ifu_retire1_condbr,io.rtu_bht.rtu_ifu_retire2_condbr)
  when(rtu_condbr_cnt === 0.U) {
    rtu_ghr_pre := rtu_ghr_reg
  }.elsewhen(rtu_condbr_cnt === "b001".U || rtu_condbr_cnt === "b010".U || rtu_condbr_cnt === "b001".U) {
    rtu_ghr_pre := Cat(rtu_ghr_reg(20,0) , 1.U)
  }.elsewhen(rtu_condbr_cnt === "b111".U) {
    rtu_ghr_pre := Cat(rtu_ghr_reg(18,0), "b111".U)
  }.otherwise {
    rtu_ghr_pre := Cat(rtu_ghr_reg(19,0), "b11".U)
  }

  when(io.rtu_ifu_flush) {
    vghr := rtu_ghr_reg
  }.elsewhen(io.ipctrl_bht_con_br_vld) {
    vghr := Cat(vghr(20,0),io.ipctrl_bht_con_br_taken)
  }.otherwise {
    vghr := vghr
  }

  val pre_update_wen = (io.cur_condbr_taken & (io.cur_pre_rst =/= "b11".U)) | (!io.cur_condbr_taken & (io.cur_pre_rst =/= "b00".U))
  val sel_update_wen = (io.cur_condbr_taken & (io.cur_sel_rst =/= "b11".U)) | (!io.cur_condbr_taken & (io.cur_sel_rst =/= "b00".U))
  val pre_update_data = Mux(pre_update_wen===1.U,io.cur_pre_rst+1.U,io.cur_pre_rst-1.U)
  val sel_update_data = Mux(sel_update_wen===1.U,io.cur_sel_rst+1.U,io.cur_sel_rst-1.U)

  when(pre_update_wen === 1.U) {
    pre_array(Cat(vghr(12,9),vghr(8,3)^vghr(20,15)))(io.cur_pc(6,3)^io.cur_ghr(3,0)) := pre_update_data
  }.otherwise {
    pre_array := pre_array
  }
  when(sel_update_wen === 1.U) {
    sel_array(sel_array_index)(sel_array_data_index) := sel_update_data
  }.otherwise {
    sel_array := sel_array
  }
  io.rtu_ghr := rtu_ghr_reg
  io.bht_ghr := vghr
}
