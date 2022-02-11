package core.lsu
import chisel3._
import chisel3.util._


class StoreQueueData extends CoreBundle {
  val valid = Bool()
  val rob_idx = new ROBPtr

  val optype = (FuncOpType.uwidth)
  val mask  = UInt((XLEN/8).W)

  val vaddr = UInt(VAddrBits.W)
  val paddr = UInt(PAddrBits.W)
  val addr_valid = Bool()
  val data  = UInt(XLEN.W)
  val data_valid = Bool()

  val commit = Bool()
}

class StoreQueueIO extends CoreBundle {
  val store_enqueue = Vec(3,Flipped(Decoupled(new LSQEnqueue)))
  val SQ_idx = Output(new LSQPtr)

  val store_addr = Flipped(Valid(new AddrRS))
  val store_data = Flipped(Valid(new DataRS))

  val store_wb = Valid(new LSU_WB)
  val store_commit = Vec(3, Input(Bool()))

  val store_dequeue  = Decoupled(new StoreQueueData)

  val fw_check = new ForwardCheck
}

class StoreQueue extends Module with Config with HasCircularQueuePtrHelper {
  def genWmask(sizeEncode: UInt): UInt = {
    LookupTree(sizeEncode, List(
      "b00".U -> 0x1.U, //0001 << addr(2:0) 1111 1111
      "b01".U -> 0x3.U, //0011              1111 1111 1111 1111
      "b10".U -> 0xf.U, //1111              1111 1111 1111 1111 1111 1111 1111 1111
      "b11".U -> 0xff.U //11111111
    )).asUInt()
  }

  def genWdata(data: UInt, sizeEncode: UInt): UInt = {
    LookupTree(sizeEncode, List(
      "b00".U -> Cat(data(7, 0), data(7, 0), data(7, 0), data(7, 0), data(7, 0), data(7, 0), data(7, 0), data(7, 0)),
      "b01".U -> Cat(data(15, 0), data(15, 0), data(15, 0), data(15, 0)),
      "b10".U -> Cat(data(31, 0), data(31, 0)),
      "b11".U -> data(63, 0)
    ))
  }

  val io = IO(new StoreQueueIO)

  val SQ_data = RegInit(VecInit(Seq.fill(LSQueueSize)(0.U.asTypeOf(new StoreQueueData))))

  val enqPtr = RegInit(0.U.asTypeOf(new LSQPtr))
  val wbPtr = RegInit(0.U.asTypeOf(new LSQPtr))
  val cmtPtr = RegInit(0.U.asTypeOf(new LSQPtr))
  val deqPtr = RegInit(0.U.asTypeOf(new LSQPtr))

  val validEntries = distanceBetween(enqPtr, deqPtr)

  //enqueue
  val allowEnq = validEntries <= (LSQueueSize - 3).U
  val numEnq = PopCount(io.store_enqueue.map(_.valid && allowEnq))
  for (i <- 0 until 3) {
    when(io.store_enqueue(i).valid && allowEnq) {
      SQ_data(enqPtr.value + i.U).valid := true.B
      SQ_data(enqPtr.value + i.U).rob_idx := io.store_enqueue(i).bits.rob_idx
      SQ_data(enqPtr.value + i.U).optype := io.store_enqueue(i).bits.optype
      SQ_data(enqPtr.value + i.U).data_valid := false.B
      SQ_data(enqPtr.value + i.U).addr_valid := false.B
      SQ_data(enqPtr.value + i.U).commit := false.B
    }
    io.store_enqueue(i).ready := allowEnq
  }
  enqPtr := enqPtr + numEnq
  io.SQ_idx := enqPtr

  //addr
  when(io.store_addr.valid) {
    SQ_data(io.store_addr.bits.lsq_idx.value).vaddr := io.store_addr.bits.vaddr
    SQ_data(io.store_addr.bits.lsq_idx.value).paddr := io.store_addr.bits.paddr
    SQ_data(io.store_addr.bits.lsq_idx.value).addr_valid := true.B
    SQ_data(io.store_addr.bits.lsq_idx.value).mask :=
      genWmask(SQ_data(io.store_addr.bits.lsq_idx.value).optype(1, 0)) << (io.store_addr.bits.paddr(2, 0) << 3.U)
  }
  //data
  when(io.store_data.valid) {
    SQ_data(io.store_data.bits.lsq_idx.value).data_valid := true.B
    SQ_data(io.store_data.bits.lsq_idx.value).data :=
      genWdata(io.store_data.bits.data, SQ_data(io.store_data.bits.lsq_idx.value).optype(1, 0))
  }

  //wb to rob
  val wb_valid = SQ_data(wbPtr.value).valid && SQ_data(wbPtr.value).addr_valid && SQ_data(wbPtr.value).data_valid
  when(wb_valid) {
    wbPtr := wbPtr + 1.U
  }
  io.store_wb.valid := wb_valid
  io.store_wb.bits.rob_idx := SQ_data(wbPtr.value).rob_idx

  //rob commit
  for (i <- 0 until 3) {
    when(io.store_commit(i)) {
      SQ_data(cmtPtr.value + i.U).commit := true.B
    }
  }
  cmtPtr := cmtPtr + PopCount(io.store_commit)

  //dequeue
  io.store_dequeue.bits := SQ_data(deqPtr.value)
  io.store_dequeue.valid := SQ_data(deqPtr.value).valid && SQ_data(deqPtr.value).commit
  when(io.store_dequeue.fire()) {
    SQ_data(deqPtr.value).valid := false.B
    deqPtr := deqPtr + 1.U
  }

  //forward check
  val deqMask = UIntToMask(deqPtr.value, LSQueueSize)
  val fw_Mask = UIntToMask(io.fw_check.storeQ_fw_idx.value, LSQueueSize)
  val same_flag = io.fw_check.storeQ_fw_idx.flag === deqPtr.flag

  val SQ_forward_mask = Mux(same_flag, deqMask ^ fw_Mask, !deqMask | fw_Mask)

  val allValid = WireInit(VecInit((0 until LSQueueSize).map(i => SQ_data(i).valid && SQ_data(i).addr_valid && SQ_data(i).data_valid)))
  val fw_invalid = (SQ_forward_mask & !allValid.asUInt).orR

  val need_fw = WireInit(VecInit(Seq.fill(LSQueueSize)(false.B)))
  for (i <- 0 until StoreBufferSize) {
    need_fw(i) := SQ_forward_mask(i) && SQ_data(i).paddr === io.fw_check.paddr && (SQ_data(i).mask ^ io.fw_check.mask).orR
  }

  val fw_sel = ParallelPriorityEncoder(need_fw)
  io.fw_check.fw_inv := PopCount(need_fw) > 1.U || fw_invalid
  io.fw_check.fw_data := SQ_data(fw_sel).data
  io.fw_check.fw_mask := SQ_data(fw_sel).mask ^ io.fw_check.mask


}

