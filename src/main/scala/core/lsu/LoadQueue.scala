package core.lsu
import chisel3._
import chisel3.util._


class LoadQueueData extends  CoreBundle {
  val valid = Bool()
  val rob_idx = new ROBPtr

  val optype = (FuncOpType.uwidth)
  val mask  = UInt((XLEN/8).W)

  val vaddr = UInt(VAddrBits.W)
  val paddr = UInt(PAddrBits.W)
  val addr_valid = Bool()
  val data  = UInt(XLEN.W)
  val data_valid = Bool()
  val reg_addr = UInt(NRPhyRegs.W)

  val need_replay =Bool()
  val storeQ_fw_idx = new LSQPtr
}

class LoadQueueIO extends CoreBundle {
  val load_enqueue = Vec(3,Decoupled(new LSQEnqueue))
  val LQ_idx = Output(new LSQPtr)

  val load_addr = Flipped(Valid(new AddrRS))
  val load_data = Flipped(Valid(new LoadData))

  val load_replay = Valid(new LoadReplay)
}

class LoadQueue extends Module with Config with HasCircularQueuePtrHelper {
  val io = IO(new LoadQueueIO)

  val LQ_data = RegInit(VecInit(Seq.fill(LSQueueSize)(0.U.asTypeOf(new LoadQueueData))))
  val LQ_wait_cnt = RegInit(VecInit(Seq.fill(LSQueueSize)(0.U(4.W))))

  val enqPtr = RegInit(0.U.asTypeOf(new LSQPtr))
  val deqPtr = RegInit(0.U.asTypeOf(new LSQPtr))

  val validEntries = distanceBetween(enqPtr, deqPtr)

  //enqueue
  val allowEnq = validEntries <= (LSQueueSize - 3).U
  val numEnq   = PopCount(io.load_enqueue.map(_.valid && allowEnq))
  for(i <- 0 until 3){
    when(io.load_enqueue(i).valid && allowEnq){
      LQ_data(enqPtr.value + i.U).valid         := true.B
      LQ_data(enqPtr.value + i.U).rob_idx       := io.load_enqueue(i).bits.rob_idx
      LQ_data(enqPtr.value + i.U).optype        := io.load_enqueue(i).bits.optype
      LQ_data(enqPtr.value + i.U).data_valid    := false.B
      LQ_data(enqPtr.value + i.U).addr_valid    := false.B
      LQ_data(enqPtr.value + i.U).need_replay   := false.B
      LQ_data(enqPtr.value + i.U).storeQ_fw_idx := io.load_enqueue(i).bits.storeQ_fw_idx
      LQ_wait_cnt(enqPtr.value + i.U)           := 0.U
    }
    io.load_enqueue(i).ready := allowEnq
  }
  enqPtr := enqPtr + numEnq
  io.LQ_idx := enqPtr

  //addr
  when(io.load_addr.valid){
    LQ_data(io.load_addr.bits.lsq_idx.value).vaddr      := io.load_addr.bits.vaddr
    LQ_data(io.load_addr.bits.lsq_idx.value).paddr      := io.load_addr.bits.paddr
    LQ_data(io.load_addr.bits.lsq_idx.value).addr_valid := true.B
    LQ_data(io.load_addr.bits.lsq_idx.value).reg_addr   := io.load_addr.bits.reg_addr
  }
  //data
  when(io.load_data.valid){
    LQ_data(io.load_addr.bits.lsq_idx.value).mask        := io.load_data.bits.mask
    LQ_data(io.load_data.bits.lsq_idx.value).data        := io.load_data.bits.data
    LQ_data(io.load_data.bits.lsq_idx.value).data_valid  := !io.load_data.bits.need_replay
    LQ_data(io.load_data.bits.lsq_idx.value).need_replay := io.load_data.bits.need_replay
    when(io.load_data.bits.need_replay){
      LQ_wait_cnt(io.load_data.bits.lsq_idx.value)       := 15.U
    }
  }
  for(i <- 0 until LSQueueSize){
    LQ_wait_cnt(i) := Mux(LQ_wait_cnt(i) === 0.U, 0.U, LQ_wait_cnt(i) - 1.U)
  }

  //replay
  val replayValid = LQ_data.zip(LQ_wait_cnt).map(v_num => v_num._1.need_replay && (v_num._2 === 0.U))
  val replaySelect = ParallelPriorityEncoder(replayValid)
  val replayPtr = WireInit(0.U.asTypeOf(new LSQPtr))
  replayPtr.value := replaySelect
  io.load_replay.valid := LQ_data(replaySelect).valid && LQ_data(replaySelect).need_replay && LQ_wait_cnt(replaySelect) === 0.U
  io.load_replay.bits.lsq_idx         := replayPtr
  io.load_replay.bits.paddr           := LQ_data(replaySelect).paddr
  io.load_replay.bits.optype          := LQ_data(replaySelect).optype
  io.load_replay.bits.storeQ_fw_idx   := LQ_data(replaySelect).storeQ_fw_idx
  io.load_replay.bits.reg_addr        := LQ_data(replaySelect).reg_addr
  io.load_replay.bits.rob_idx         := LQ_data(replaySelect).rob_idx

  //dequeue
  val deq_valid = LQ_data(deqPtr.value).valid && LQ_data(deqPtr.value).addr_valid && LQ_data(deqPtr.value).data_valid
  when(deq_valid){
    LQ_data(deqPtr.value).valid := false.B
    deqPtr := deqPtr + 1.U
  }
}
