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

class ICacheReq extends CoreBundle {
  val vaddr = UInt(VAddrBits.W)
  val paddr = UInt(PAddrBits.W)
}

class ICacheResp extends CoreBundle {
  val inst_data = Vec(8,UInt(16.W))   //h0 +8
  val predecode = Vec(8,UInt(4.W))
}

class IFU_TLB extends CoreBundle {
  val vaddr = ValidIO(UInt(VAddrBits.W))
  val paddr = Flipped(ValidIO(UInt(PAddrBits.W)))
  val tlb_miss = Input(Bool())
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
  val pcall = Bool()
  val pret  = Bool()
  val push_pc = UInt(VAddrBits.W)
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
class IBStageIO extends CoreBundle {
  val pc          = Input(UInt(VAddrBits.W))
  val ip2ib       = Flipped(Valid(new IP2IB))
  val ib_redirect = Valid(UInt(VAddrBits.W))
  val ind_jmp_valid  = Output(Bool())
  val ind_btb_target = Input(UInt(20.W))
  val btbmiss        = Output(Bool())
  val ubtb_update    = Output(new uBTBUpdateData)
  val ras_push_pc    = Output(UInt(VAddrBits.W))
}
class chgflw extends CoreBundle {
  val vld = Input(Bool())
  val pc  = Input(VAddrBits.W)
  val pred = Input(UInt(2.W))
  val vlmu = Input(UInt(2.W))
  val vsew = Input(UInt(3.W))
  val vl   = Input(UInt(8.W))
}
class BPUUpdate extends CoreBundle {

}

class IFUIO extends CoreBundle {
  //flush
  val bru_redirect = Flipped(Valid(UInt(VAddrBits.W)))
  //inst fetch
  val tlb        = new IFU_TLB
  val cache_req  = Valid(new ICacheReq)
  val cache_resp = Flipped(Valid(new ICacheResp))
  //bht, btb update
  val bpu_update = new BPUUpdate
}