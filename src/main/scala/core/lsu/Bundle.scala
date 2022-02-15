package core.lsu
import chisel3._
import chisel3.util._


class LSQEnqueue extends CoreBundle {
  val rob_idx = new ROBPtr
  val optype = (FuncOpType.uwidth)
  val storeQ_fw_idx = new LSQPtr
}

class LSQ_idx extends CoreBundle {
  val LQ_idx = new LSQPtr
  val SQ_idx = new LSQPtr
}

class AddrRS extends CoreBundle {
  val lsq_idx = new LSQPtr
  val vaddr = UInt(VAddrBits.W)
  val paddr = UInt(PAddrBits.W)
  val optype = (FuncOpType.uwidth)
  val reg_addr = UInt(NRPhyRegs.W)
  val rob_idx = new ROBPtr
}

class DataRS extends CoreBundle {
  val lsq_idx = new LSQPtr
  val data = UInt(XLEN.W)
}

class LoadData extends CoreBundle {
  val lsq_idx = new LSQPtr
  val data = UInt(XLEN.W)
  val mask = UInt((XLEN/8).W)
  val need_replay = Bool()
}

class LoadReplay extends CoreBundle {
  val lsq_idx = new LSQPtr
  val paddr = UInt(PAddrBits.W)
  val vaddr = UInt(VAddrBits.W)
  val optype = (FuncOpType.uwidth)
  val storeQ_fw_idx = new LSQPtr
  val reg_addr = UInt(NRPhyRegs.W)
  val rob_idx = new ROBPtr
}

class ForwardCheck extends CoreBundle {
  val paddr = Input(UInt(PAddrBits.W))
  val storeQ_fw_idx = Input(new LSQPtr)
  val mask  = Input(UInt((XLEN/8).W))

  val fw_mask = Output(UInt((XLEN/8).W))
  val fw_data = Output(UInt(XLEN.W))
  val fw_inv  = Output(Bool())
}

class LSU_WB extends CoreBundle {
  val rob_idx = new ROBPtr
  val load_res = UInt(XLEN.W)
  val reg_addr = UInt(NRPhyRegs.W)
  //TODO: Exceptions
}

class LSU_TLB extends CoreBundle {
  val vaddr = ValidIO(UInt(VAddrBits.W))
  val paddr = Flipped(ValidIO(UInt(PAddrBits.W)))
  val tlb_miss = Input(Bool())
}

class Load_Cache extends CoreBundle {
  val paddr = ValidIO(UInt(PAddrBits.W))
  val vaddr = Output(UInt(VAddrBits.W))
  //one cycle
  val res   = Flipped(ValidIO(UInt(XLEN.W)))
  val miss  = Input(Bool())
}

class Store_Cache extends CoreBundle {
  val valid = Output(Bool())
  val vaddr = Output(UInt(VAddrBits.W))
  val paddr = Output(UInt(PAddrBits.W))
  val data  = Output(UInt(XLEN.W))
  val mask  = Output(UInt((XLEN/8).W))
  val miss  = Input(Bool())
}

class LSUIO extends CoreBundle {
  //enqueue
  val load_enqueue = Vec(3,Flipped(Decoupled(new LSQEnqueue)))
  val store_enqueue = Vec(3,Flipped(Decoupled(new LSQEnqueue)))
  val lsq_idx = Output(new LSQ_idx)
  //RS
  val load_addr  = Flipped(Decoupled(new AddrRS))
  val storeQ_fw_idx = Input(new LSQPtr)
  val store_addr = Flipped(Decoupled(new AddrRS))
  val store_data = Flipped(Valid(new DataRS))
  //WB
  val lsu_wb = Vec(2,Valid(new LSU_WB))
  //store commit
  val store_commit = Vec(3, Input(Bool()))
  //to TLB
  val loadTLB = new LSU_TLB
  val storeTLB = new LSU_TLB
  //to Cache
  val load_cache  = new Load_Cache
  val store_cache = new Store_Cache
  //TODO: MMIO interface
}

