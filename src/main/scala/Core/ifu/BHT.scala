package Core.ifu
import Core.{Config, CoreBundle}
import chisel3._
import chisel3.util._

class BHTUpdate extends CoreBundle {
  val cur_pc  = UInt(VAddrBits.W)
  val cur_ghr = UInt(22.W)
  val cur_condbr_taken = Bool()
  val sel_res = UInt(2.W)
}

class BHTIO extends CoreBundle {
  val pc                  = Input(UInt(VAddrBits.W))
  val bht_resp            = Output(new BHT_IP_Resp)
  val ip_bht_con_br_taken = Input(Bool())
  val ip_bht_con_br_vld   = Input(Bool())
  val bht_ghr             = Output(UInt(8.W))
  val rtu_ghr             = Output(UInt(8.W))
  val rtu_ifu_flush       = Input(Bool())

  val rtu_retire_condbr       = Input(Vec(3,Bool()))
  val rtu_retire_condbr_taken = Input(Vec(3,Bool()))

  val bht_update = Flipped(Valid(new BHTUpdate))

}
class BHT extends Module with Config {
  val io = IO(new BHTIO)
  val sel_array = RegInit(VecInit(Seq.fill(128)(VecInit(Seq.fill(8)(0.U(2.W))))))
  val pre_array = RegInit(VecInit(Seq.fill(1024)(VecInit(Seq.fill(2)(VecInit(Seq.fill(16)(0.U(2.W))))))))
  val vghr = RegInit(0.U(22.W))
  val vghr_pre = WireInit(0.U(22.W))
  val rtu_ghr_reg = RegInit(0.U(22.W))
  val rtu_ghr_pre = WireInit(0.U(22.W))

  //read
  val sel_array_index  = io.pc(10,4)
  val sel_array_offset = io.pc(3,1)
  val pre_sel = sel_array(sel_array_index)(sel_array_offset)

  //{vghr[12:9], {vghr_reg[8:3]^vghr_reg[20:15]}} is the basic index of read
  val pre_array_index  = Cat(vghr_pre(12,9),vghr_pre(8,3) ^ vghr_pre(20,15))
  val pre_array_offset = vghr_pre(3,0) ^ io.pc(7,4)
  val pre_array_data = pre_array(pre_array_index)
  val pre_taken_data = pre_array_data(0)
  val pre_ntaken_data = pre_array_data(1)

  io.bht_resp.pre_taken  := RegNext(pre_taken_data)
  io.bht_resp.pre_ntaken := RegNext(pre_ntaken_data)
  io.bht_resp.pre_offset := RegNext(pre_array_offset)
  io.bht_resp.pre_sel    := RegNext(pre_sel)

  //vghr
  //suppose only one con_br
  when(io.rtu_ifu_flush) {
    vghr     := rtu_ghr_pre
    vghr_pre := rtu_ghr_pre
  }.elsewhen(io.ip_bht_con_br_vld) {
    vghr     := Cat(vghr(20,0),io.ip_bht_con_br_taken)
    vghr_pre := Cat(vghr(20,0),io.ip_bht_con_br_taken)
  }.otherwise {
    vghr_pre := vghr
  }

  //to ib ind_btb
  io.bht_ghr := RegNext(vghr)

  //rtu_ghr update
  val rtu_condbr       = io.rtu_retire_condbr.asUInt()
  val rtu_condbr_taken = io.rtu_retire_condbr_taken.asUInt()
  rtu_ghr_pre := MuxLookup(rtu_condbr, rtu_ghr_reg, Seq(
    "b000".U -> rtu_ghr_reg,
    "b001".U -> Cat(rtu_ghr_reg(20,0), rtu_condbr_taken(0)),
    "b010".U -> Cat(rtu_ghr_reg(20,0), rtu_condbr_taken(1)),
    "b100".U -> Cat(rtu_ghr_reg(20,0), rtu_condbr_taken(2)),
    "b011".U -> Cat(rtu_ghr_reg(19,0), rtu_condbr_taken(0), rtu_condbr_taken(1)),
    "b101".U -> Cat(rtu_ghr_reg(19,0), rtu_condbr_taken(0), rtu_condbr_taken(2)),
    "b110".U -> Cat(rtu_ghr_reg(19,0), rtu_condbr_taken(1), rtu_condbr_taken(2)),
    "b111".U -> Cat(rtu_ghr_reg(18,0), rtu_condbr_taken(0), rtu_condbr_taken(1), rtu_condbr_taken(2))
  ))

  when(rtu_condbr.orR()){
    rtu_ghr_reg := rtu_ghr_pre
  }

  io.rtu_ghr := rtu_ghr_reg

  //bht update
  val cur_pc  = io.bht_update.bits.cur_pc
  val cur_ghr = io.bht_update.bits.cur_ghr
  val cur_condbr_taken = io.bht_update.bits.cur_condbr_taken
  val cur_sel_res      = io.bht_update.bits.sel_res

  val cur_sel_array_index  = cur_pc(10,4)
  val cur_sel_array_offset = cur_pc(3,1)
  val cur_pre_array_index  = Cat(cur_ghr(13,10),cur_ghr(9,4) ^ cur_ghr(21,16))
  val cur_pre_array_offset = cur_ghr(4,1) ^ cur_pc(7,4)

  val cur_pre_rst = pre_array(cur_pre_array_index)(cur_sel_res(1))(cur_pre_array_offset)
  val cur_sel_rst = sel_array(cur_sel_array_index)(cur_sel_array_offset)

  val pre_update_wen = (cur_condbr_taken & (cur_pre_rst =/= "b11".U)) | (!cur_condbr_taken & (cur_pre_rst =/= "b00".U))
  val sel_update_wen = (cur_condbr_taken & (cur_sel_rst =/= "b11".U)) | (!cur_condbr_taken & (cur_sel_rst =/= "b00".U))
  val pre_update_data = Mux(cur_condbr_taken,cur_pre_rst+1.U,cur_pre_rst-1.U)
  val sel_update_data = Mux(cur_condbr_taken,cur_sel_rst+1.U,cur_sel_rst-1.U)

  when(io.bht_update.valid){
    when(pre_update_wen){
      pre_array(cur_pre_array_index)(cur_sel_res(1))(cur_pre_array_offset) := pre_update_data
    }
    when(sel_update_wen){
      sel_array(cur_sel_array_index)(cur_sel_array_offset) := sel_update_data
    }
  }

}
