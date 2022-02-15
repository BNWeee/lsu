package core.lsu
import chisel3._
import chisel3.util._

class ROBPtr extends CircularQueuePtr[ROBPtr](Config.RobSize) with HasCircularQueuePtrHelper{
  override def cloneType = (new ROBPtr).asInstanceOf[this.type]
}

class LSQPtr extends CircularQueuePtr[LSQPtr](Config.LSQueueSize) with HasCircularQueuePtrHelper{
  override def cloneType = (new LSQPtr).asInstanceOf[this.type]
}

class LSU extends Module with Config {

  def genWmask(sizeEncode: UInt): UInt = {
    LookupTree(sizeEncode, List(
      "b00".U -> 0x1.U, //0001 << addr(2:0) 1111 1111
      "b01".U -> 0x3.U, //0011              1111 1111 1111 1111
      "b10".U -> 0xf.U, //1111              1111 1111 1111 1111 1111 1111 1111 1111
      "b11".U -> 0xff.U //11111111
    )).asUInt()
  }

  val io = IO(new LSUIO)
  val loadQ  = Module(new LoadQueue)
  val storeQ = Module(new StoreQueue)
  val storeB = Module(new StoreBuffer)

  //load store enqueue
  io.load_enqueue  <> loadQ.io.load_enqueue
  io.store_enqueue <> storeQ.io.store_enqueue
  io.lsq_idx.LQ_idx := loadQ.io.LQ_idx
  io.lsq_idx.SQ_idx := storeQ.io.SQ_idx

  //load stage1
  //priority 1.Load Queue Replay 2.Load Addr RS
  val load_replay_valid = loadQ.io.load_replay.valid
  //load vaddr to TLB
  io.loadTLB.vaddr.valid := io.load_addr.valid && !load_replay_valid//check loadQ replay
  io.loadTLB.vaddr.bits  := io.load_addr.bits.vaddr
  io.load_addr.ready     := !io.loadTLB.tlb_miss && !load_replay_valid
  //TLB response to LoadQueue
  loadQ.io.load_addr.bits := io.load_addr.bits
  loadQ.io.load_addr.valid := io.loadTLB.paddr.valid && !load_replay_valid
  loadQ.io.load_addr.bits.paddr := io.loadTLB.paddr.bits
  //to Dcache
  io.load_cache.paddr.valid := io.loadTLB.paddr.valid || load_replay_valid
  io.load_cache.paddr.bits  := Mux(load_replay_valid, loadQ.io.load_replay.bits.paddr, io.loadTLB.paddr.bits)
  io.load_cache.vaddr       := Mux(load_replay_valid, loadQ.io.load_replay.bits.vaddr, io.load_addr.bits.vaddr)

  //load pipeline reg
  val load_pipeline_valid  = RegNext(io.loadTLB.paddr.valid || load_replay_valid)
  val load_pipeline_paddr  = RegNext(Mux(load_replay_valid, loadQ.io.load_replay.bits.paddr, io.loadTLB.paddr.bits))
  val load_pipeline_optype = RegNext(Mux(load_replay_valid, loadQ.io.load_replay.bits.optype, io.load_addr.bits.optype))
  val load_pipeline_LQ_idx = RegNext(Mux(load_replay_valid, loadQ.io.load_replay.bits.lsq_idx, io.load_addr.bits.lsq_idx))
  val load_pipeline_fw_idx = RegNext(Mux(load_replay_valid, loadQ.io.load_replay.bits.storeQ_fw_idx, io.storeQ_fw_idx))
  val load_pipeline_wbaddr = RegNext(Mux(load_replay_valid, loadQ.io.load_replay.bits.reg_addr, io.load_addr.bits.reg_addr))
  val load_pipeline_robidx = RegNext(Mux(load_replay_valid, loadQ.io.load_replay.bits.rob_idx, io.load_addr.bits.rob_idx))

  //load stage2
  val load_mask = genWmask(load_pipeline_optype(1,0)) << (load_pipeline_paddr(2, 0) << 3.U)
  //forward check
  storeQ.io.fw_check.paddr         := load_pipeline_paddr
  storeQ.io.fw_check.mask          := load_mask
  storeQ.io.fw_check.storeQ_fw_idx := load_pipeline_fw_idx
  storeB.io.fw_check.paddr         := load_pipeline_paddr
  storeB.io.fw_check.mask          := load_mask
  storeB.io.fw_check.storeQ_fw_idx := load_pipeline_fw_idx

  val load_data_vec = WireInit(VecInit(Seq.fill(8)(0.U(8.W))))
  for(i <- 0 until 8){
    load_data_vec(i) := Mux(storeQ.io.fw_check.fw_mask(i), storeQ.io.fw_check.fw_data(i*8+7,i*8),
      Mux(storeB.io.fw_check.fw_mask(i), storeB.io.fw_check.fw_data(i*8+7,i*8), io.load_cache.res.bits(i*8+7,i*8)))
  }
  //data select
  val load_res_sel = load_data_vec.asUInt >> (load_pipeline_paddr(2, 0) << 3.U)
  val load_data = LookupTree(load_pipeline_optype, List(
    LSUOpType.lb   -> SignExt(load_res_sel(7, 0) , XLEN),
    LSUOpType.lh   -> SignExt(load_res_sel(15, 0), XLEN),
    LSUOpType.lw   -> SignExt(load_res_sel(31, 0), XLEN),
    LSUOpType.ld   -> SignExt(load_res_sel(63, 0), XLEN),
    LSUOpType.lbu  -> ZeroExt(load_res_sel(7, 0) , XLEN),
    LSUOpType.lhu  -> ZeroExt(load_res_sel(15, 0), XLEN),
    LSUOpType.lwu  -> ZeroExt(load_res_sel(31, 0), XLEN)
  ))

  //replay control
  loadQ.io.load_data.valid            := load_pipeline_valid && io.load_cache.res.valid
  loadQ.io.load_data.bits.lsq_idx     := load_pipeline_LQ_idx
  loadQ.io.load_data.bits.data        := load_data
  loadQ.io.load_data.bits.mask        := load_mask
  loadQ.io.load_data.bits.need_replay := (io.load_cache.miss && io.load_cache.res.valid) || storeQ.io.fw_check.fw_inv || storeB.io.fw_check.fw_inv

  //load wb
  io.lsu_wb(0).valid         := loadQ.io.load_data.valid && !io.load_cache.miss && !storeQ.io.fw_check.fw_inv && !storeB.io.fw_check.fw_inv
  io.lsu_wb(0).bits.rob_idx  := load_pipeline_robidx
  io.lsu_wb(0).bits.load_res := load_data
  io.lsu_wb(0).bits.reg_addr := load_pipeline_wbaddr

  //store queue addr and data
  //store vaddr to TLB
  io.storeTLB.vaddr.valid := io.store_addr.valid
  io.storeTLB.vaddr.bits  := io.store_addr.bits.vaddr
  io.store_addr.ready     := !io.storeTLB.tlb_miss
  //TLB response to StoreQueue
  storeQ.io.store_addr.bits := io.store_addr.bits
  storeQ.io.store_addr.valid := io.storeTLB.paddr.valid
  storeQ.io.store_addr.bits.paddr := io.storeTLB.paddr.bits
  //store data
  storeQ.io.store_data := io.store_data

  //store wb
  io.lsu_wb(1) := storeQ.io.store_wb
  //store commit
  storeQ.io.store_commit := io.store_commit

  //store buffer
  storeB.io.in := storeQ.io.store_dequeue
  io.store_cache <> storeB.io.out

}
