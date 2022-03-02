package Core.ifu
import Core.{Config, CoreBundle}
import chisel3._
import chisel3.util._

class IFU extends Module with Config {
  val io = IO(new IFUIO)

  //pc select
  val pc_gen = Module(new PCGen)
  pc_gen.io.redirect(0).valid := ubtb.io.ubtb_resp.valid
  pc_gen.io.redirect(0).bits  := ubtb.io.ubtb_resp.bits.target_pc
  pc_gen.io.redirect(1) := ipstage.io.ip_redirect
  pc_gen.io.redirect(2) := ibstage.io.ib_redirect
  pc_gen.io.redirect(3) := io.bru_redirect

  //IF stage
  val if_data_valid = pc_gen.io.redirect(1).valid || pc_gen.io.redirect(2).valid || pc_gen.io.redirect(3).valid
  val if_pc = pc_gen.io.pc
  val ubtb = Module(new uBTB)
  val btb  = Module(new BTB)
  val bht  = Module(new BHT)
  ubtb.io.pc := if_pc
  btb.io.pc  := if_pc
  bht.io.pc  := if_pc

  io.tlb.vaddr.valid := if_data_valid
  io.tlb.vaddr.bits  := pc_gen.io.pc
  io.cache_req.valid := if_data_valid && io.tlb.paddr.valid && !io.tlb.tlb_miss
  io.cache_req.bits.vaddr := if_pc
  io.cache_req.bits.paddr := io.tlb.paddr.bits

  //IP stage
  val ip_pc   = RegNext(pc_gen.io.pc)
  val ip_ubtb = RegNext(ubtb.io.ubtb_resp)

  val ipstage = Module(new IPStage)
  ipstage.io.pc          := ip_pc
  ipstage.io.ip_flush    := pc_gen.io.redirect(2).valid || pc_gen.io.redirect(3).valid
  ipstage.io.ubtb_resp   := ip_ubtb
  ipstage.io.btb_resp    := btb.io.btb_target
  ipstage.io.bht_resp    := bht.io.bht_resp //TODO:封装bht输出
  ipstage.io.icache_resp := io.cache_resp

  //IB stage
  val ip_out = RegNext(ipstage.io.out) //ubtb,btb,bht

  val ind_btb = Module(new indBTB)
  ind_btb.io.bht_ghr := bht.io.bht_ghr
  ind_btb.io.rtu_ghr := bht.io.rtu_ghr
  ind_btb.io.ind_btb_path := ip_out.bits.pc(7,0)
  ind_btb.io.ib_jmp_valid := ibstage.io.ind_jmp_valid

  val ibstage = Module(new IBStage)
  val ib_pc        = ip_out.bits.pc
  ibstage.io.pc    := ib_pc
  ibstage.io.ip2ib := ipstage.io.out
  ibstage.io.ind_btb_target := ind_btb.io.ind_btb_target
  ibstage.io.ip_ib_addr := RegNext(ipstage.io.br_res)

  val ras = Module(new RAS)
  ras.io.ibdp_ras_push_pc := ibstage.io.ras_push_pc
  //ip changeflow && pc mask && call return
  ras.io.ibctrl_ras_preturn_vld := ipstage.io.out.bits.pret
  ras.io.ibctrl_ras_pcall_vld   := ipstage.io.out.bits.pcall
  ibstage.io.ras_target_pc := ras.io.ras_target_pc

  //addrgen
  val addrgen = Module(new ADDRGen)
  addrgen.io.in := ibstage.io.ib2addrgen
  ubtb.io.update_data := addrgen.io.ubtb_update
  btb.io.btb_update := addrgen.io.btb_update

  //inst ibuf
  val ibuf = Module(new IBuffer)
  for(i <- 0 to 7){
    ibuf.io.in(i+1).bits.pc := Cat(ibstage.io.pc(38,4), 0.U(4.W)) + (i.U << 1.U)
    ibuf.io.in(i+1).bits.data := ip_out.bits.icache_resp.inst_data(i)
    ibuf.io.in(i+1).bits.is_inst32 := ip_out.bits.inst_32_9(i+1)
    ibuf.io.in(i+1).valid := ip_out.bits.chgflw_vld_mask(i+1)
  }
  ibuf.io.in(0).bits.pc := Cat(ibstage.io.pc(38,4), 0.U(4.W)) - 2.U
  ibuf.io.in(0).bits.data := ip_out.bits.h0_data
  ibuf.io.in(0).bits.is_inst32 := ip_out.bits.inst_32_9(0)
  ibuf.io.in(0).valid := ip_out.bits.h0_vld //ip_out.bits.chgflw_vld_mask(0)

  ibuf.io.out <> io.ifu_inst_out
}
