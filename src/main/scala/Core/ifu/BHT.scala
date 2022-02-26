package Core.ifu
import Core.{Config, CoreBundle}
import chisel3._
import chisel3.util._
class BHTIO extends  CoreBundle {
  val pc = Input(UInt(VAddrBits.W))
  val ipctrl_bht_con_br_taken = Input(UInt(1.W))
  val cur_condbr_taken = Input(UInt(1.W))
  val cur_pre_rst      = Input(UInt(2.W))
  val cur_sel_rst      = Input(UInt(2.W))
  val bht_ipdp_pre_array_data_ntaken = Output(UInt(pre_array_data_size.W))
  val bht_ipdp_pre_array_data_taken = Output(UInt(pre_array_data_size.W))
  val bht_ipdp_sel_array_result = Output(UInt(2.W))
  val bht_ipdp_vghr = Output(UInt(ghr_size.W))
}
class BHT extends Module with Config {
  val io = IO(new BHTIO)
  val sel_array = RegInit(VecInit(Seq.fill(128)(VecInit(Seq.fill(8)(0.U(2.W))))))
  val pre_array = RegInit(VecInit(Seq.fill(1024)(VecInit(Seq.fill(2)(0.U(32.W))))))
  val vghr = RegInit(0.U(22.W))

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

  //update
  vghr := Cat(vghr(20,0),io.ipctrl_bht_con_br_taken)
  val pre_update_wen = (io.cur_condbr_taken & (io.cur_pre_rst =/= "b11".U)) | (!io.cur_condbr_taken & (io.cur_pre_rst =/= "b00".U))
  val sel_update_wen = (io.cur_condbr_taken & (io.cur_sel_rst =/= "b11".U)) | (!io.cur_condbr_taken & (io.cur_sel_rst =/= "b00".U))
  val pre_update_data = Mux(pre_update_wen===1.U,io.cur_pre_rst+1.U,io.cur_pre_rst-1.U)
  val sel_update_data = Mux(sel_update_wen===1.U,io.cur_sel_rst+1.U,io.cur_sel_rst-1.U)
  
}
