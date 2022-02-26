package Core.ifu
import Core.{Config, CoreBundle}
import chisel3._
import chisel3.util._

class PCGenIO extends CoreBundle{
  //0 IFStage, 1 IPStage, 2 IBStage, 3 BRU
  val redirect = Vec(4,Flipped(Valid(UInt(VAddrBits.W))))
  val pc = Output(UInt(VAddrBits.W))
}

class BHT_IP_Resp extends CoreBundle {
  val pre_taken  = Vec(16,UInt(2.W))
  val pre_ntaken = Vec(16,UInt(2.W))
  val pre_offset = UInt(4.W)
  val pre_sel    = UInt(2.W)
}

class ICacheResp extends CoreBundle {
  val inst_data = Vec(8,UInt(16.W))
  val predecode = Vec(8,UInt(4.W))
}

class IP2IB extends CoreBundle {
  val pc = UInt(VAddrBits.W)
  val icache_resp = new ICacheResp
  val decode_info = new IPDecodeOutput

  val bht_resp  = new BHT_IP_Resp
  val btb_valid = Bool()
  val btb_target = UInt(20.W)
  val btb_miss = Bool()
  val btb_sel  = UInt(4.W)
  val ubtb_valid = Bool()
  val ubtb_resp = new uBTBResp
  val ubtb_miss = Bool()
  val ubtb_mispred = Bool()
}

class IPStageIO extends  CoreBundle {
  val pc = Input(UInt(VAddrBits.W))
  val ip_redirect = Valid(UInt(VAddrBits.W))
  val ip_flush = Input(Bool())

  val ubtb_resp   = Flipped(Valid(new uBTBResp))  //RegNext
  val icache_resp = Flipped(Valid(new ICacheResp))
  val bht_resp    = Input(new BHT_IP_Resp)
  val btb_resp    = Vec(4,Flipped(Valid(UInt(20.W))))

  val out = Valid(new IP2IB)
}