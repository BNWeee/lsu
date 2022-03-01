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
  val ib_pc        = RegNext(RegNext(ib_pc))
  ibstage.io.pc    := ib_pc
  ibstage.io.ip2ib := ipstage.io.out
  ibstage.io.ind_btb_target := ind_btb.io.ind_btb_target

  val ras = Module(new RAS)
  ras.io.ibdp_ras_push_pc := ibstage.io.ras_push_pc
  //ip changeflow && pc mask && call return
  ras.io.ibctrl_ras_preturn_vld := ipstage.io.out.bits.pret
  ras.io.ibctrl_ras_pcall_vld   := ipstage.io.out.bits.pcall




}
