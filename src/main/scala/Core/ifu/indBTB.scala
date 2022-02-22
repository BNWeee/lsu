package Core.ifu
import Core.{Config, CoreBundle}
import chisel3._
import chisel3.util._

class indBTBIO extends CoreBundle {
  val commit_jmp_path = Vec(3,Flipped(Valid(UInt(8.W))))//valid排序依次进入
  val rtu_jmp_mispred = Input(Bool())
  val rtu_jmp_pc      = Input(UInt(VAddrBits.W))//pc(21,1)
  val rtu_flush       = Input(Bool())

  val rtu_ghr = Input(UInt(8.W))
  val bht_ghr = Input(UInt(8.W))
  val ind_btb_path = Input(UInt(8.W))//pc(11,4)
  val ib_jmp_valid = Input(Bool())

  val ind_btb_target = Input(UInt(20.W))
}

class indBTB extends Module with Config {
  val io = IO(new indBTBIO)

  val target = RegInit(VecInit(Seq.fill(256)(0.U(20.W))))
  val path_reg     = RegInit(VecInit(Seq.fill(4)(0.U(8.W))))
  val rtu_path_reg = RegInit(VecInit(Seq.fill(4)(0.U(8.W))))

  val path_reg_pre     = WireInit(VecInit(Seq.fill(4)(0.U(8.W))))
  val rtu_path_reg_pre = WireInit(VecInit(Seq.fill(4)(0.U(8.W))))

  val commit_jmp_cnt = PopCount(io.commit_jmp_path.map(_.valid))
  for(i <- 0 until 4){
    rtu_path_reg_pre(i) := Mux(i.U < commit_jmp_cnt, io.commit_jmp_path(commit_jmp_cnt-(i+1).U).bits, rtu_path_reg(i.U-commit_jmp_cnt))
  }
  when(commit_jmp_cnt > 0.U){
    rtu_path_reg := rtu_path_reg_pre
  }

  val path_reg_update = io.rtu_flush || io.rtu_jmp_mispred
  for(i <- 1 until 4){
    path_reg_pre(i) := Mux(path_reg_update, rtu_path_reg_pre(i), Mux(io.ib_jmp_valid, path_reg(i-1), path_reg(i)))
  }
  path_reg_pre(0) := Mux(path_reg_update, rtu_path_reg_pre(0), Mux(io.ib_jmp_valid, io.ind_btb_path, path_reg(0)))

  val wr_idx = WireInit(0.U(8.W))
  val rd_idx = WireInit(0.U(8.W))
  for(i <- 0 until 4){
    wr_idx(2*i+1,2*i) := rtu_path_reg_pre(i)(2*i+1,2*i) ^ io.rtu_ghr(2*i+1,2*i)
    rd_idx(2*i+1,2*i) := path_reg_pre(i)(2*i+1,2*i) ^ io.bht_ghr(2*i+1,2*i)
  }

  when(io.rtu_jmp_mispred){
    target(wr_idx) := io.rtu_jmp_pc(21,1)
  }

  io.ind_btb_target := target(rd_idx)

}
