package Core.top
import Core.ifu.{IFU, icache_predec}
import Core.utils.RAMHelper
import Core.{Config, CoreBundle}
import chisel3._
import chisel3.util._
import difftest._
import firrtl.transforms.DontTouchAnnotation

class SimTopIO extends Bundle {
  val logCtrl = new LogCtrlIO
  val perfInfo = new PerfInfoIO
  val uart = new UARTIO
}
class SimTop extends Module with Config {
  val io : SimTopIO = IO(new SimTopIO)
  io.uart.in.valid  := false.B
  io.uart.out.valid := false.B
  io.uart.out.ch    := 0.U

  val ifu       = Module(new IFU)

  //cache resp emu
  val icachepredecode = Module(new icache_predec)
  val ram1 = Module(new RAMHelper)
  ram1.io.clk := clock
  ram1.io.en  := !reset.asBool()
  ram1.io.rIdx := (PcStart.U) >> 3
  ram1.io.wIdx := DontCare
  ram1.io.wen  := false.B
  ram1.io.wdata := DontCare
  ram1.io.wmask := DontCare
  val inst_low = ram1.io.rdata
  val ram2 = Module(new RAMHelper)
  ram2.io.clk := clock
  ram2.io.en  := !reset.asBool()
  ram2.io.rIdx := (PcStart.U) >> 3
  ram2.io.wIdx := DontCare
  ram2.io.wen  := false.B
  ram2.io.wdata := DontCare
  ram2.io.wmask := DontCare
  val inst_high = ram2.io.rdata
  for(i <- 0 until 4) {
    ifu.io.cache_resp.bits.inst_data(i) := inst_low((16*(i+1))-1,16*i)
    icachepredecode.io.din(i) := inst_low((16*(i+1))-1,16*i)
    ifu.io.cache_resp.bits.predecode(i) := icachepredecode.io.dout(i)
  }
  for(i <- 0 until 4) {
    ifu.io.cache_resp.bits.inst_data(i+4) := inst_high((16*(i+1))-1,16*i)
    icachepredecode.io.din(i+4) := inst_low((16*(i+1))-1,16*i)
    ifu.io.cache_resp.bits.predecode(i+4) := icachepredecode.io.dout(i+4)
  }
  ifu.io.cache_resp.valid := true.B

  //tlb emu
  ifu.io.tlb.paddr := ifu.io.tlb.vaddr
  ifu.io.tlb.tlb_miss := false.B

  //cache req emu
  ifu.io.cache_req.ready := true.B

  //bru_redirect emu
  ifu.io.bru_redirect.bits := 0.U
  ifu.io.bru_redirect.valid := false.B

  //bpu_update emu
  ifu.io.bpu_update.rtu_flush := false.B
  ifu.io.bpu_update.rtu_retire_condbr := Seq(false.B,false.B,false.B)
  ifu.io.bpu_update.rtu_retire_condbr_taken := Seq(false.B,false.B,false.B)
  for(i <- 0 until 3) {
    ifu.io.bpu_update.ind_btb_commit_jmp_path(i).bits := 0.U
    ifu.io.bpu_update.ind_btb_commit_jmp_path(i).valid := false.B
  }
  ifu.io.bpu_update.ind_btb_rtu_jmp_pc := 0.U
  ifu.io.bpu_update.ind_btb_rtu_jmp_mispred := false.B
  ifu.io.bpu_update.rtu_ras_update.isret := false.B
  ifu.io.bpu_update.rtu_ras_update.iscall := false.B
  ifu.io.bpu_update.rtu_ras_update.target := 0.U
  ifu.io.bpu_update.bht_update.valid := false.B
  ifu.io.bpu_update.bht_update.bits.cur_pc := 0.U
  ifu.io.bpu_update.bht_update.bits.cur_ghr := 0.U
  ifu.io.bpu_update.bht_update.bits.cur_condbr_taken := false.B
  ifu.io.bpu_update.bht_update.bits.sel_res := 0.U

//  ifu.io.bpu_update := DontCare

  ifu.io.ifu_inst_out(0).ready := true.B
  ifu.io.ifu_inst_out(1).ready := true.B
  ifu.io.ifu_inst_out(2).ready := true.B

  dontTouch(ifu.io)
  dontTouch(icachepredecode.io)

  val instrCommit = Module(new DifftestInstrCommit)
  instrCommit.io.clock := clock
  instrCommit.io.coreid := 0.U
  instrCommit.io.index := 0.U
  instrCommit.io.skip := false.B
  instrCommit.io.isRVC := false.B
  instrCommit.io.scFailed := false.B

  instrCommit.io.valid := true.B
  instrCommit.io.pc    := 0.U

  instrCommit.io.instr := 0.U

  instrCommit.io.wen   := false.B
  instrCommit.io.wdata := 0.U
  instrCommit.io.wdest := 0.U


  val csrCommit = Module(new DifftestCSRState)
  csrCommit.io.clock          := clock
  csrCommit.io.priviledgeMode := 0.U
  csrCommit.io.mstatus        := 0.U
  csrCommit.io.sstatus        := 0.U
  csrCommit.io.mepc           := 0.U
  csrCommit.io.sepc           := 0.U
  csrCommit.io.mtval          := 0.U
  csrCommit.io.stval          := 0.U
  csrCommit.io.mtvec          := 0.U
  csrCommit.io.stvec          := 0.U
  csrCommit.io.mcause         := 0.U
  csrCommit.io.scause         := 0.U
  csrCommit.io.satp           := 0.U
  csrCommit.io.mip            := 0.U
  csrCommit.io.mie            := 0.U
  csrCommit.io.mscratch       := 0.U
  csrCommit.io.sscratch       := 0.U
  csrCommit.io.mideleg        := 0.U
  csrCommit.io.medeleg        := 0.U

  val cycleCnt = RegInit(0.U(64.W))
  cycleCnt := cycleCnt + 1.U
  val instrCnt = RegInit(0.U(64.W))
  when(instrCommit.io.valid){
    instrCnt := instrCnt + 1.U
  }

  val trap = Module(new DifftestTrapEvent)
  trap.io.clock    := clock
  trap.io.coreid   := 0.U
  trap.io.valid    := false.B
  trap.io.code     := 0.U // GoodTrap
  trap.io.pc       := 0.U
  trap.io.cycleCnt := cycleCnt
  trap.io.instrCnt := instrCnt
}

