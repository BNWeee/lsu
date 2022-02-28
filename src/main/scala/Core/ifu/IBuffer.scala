package Core.ifu
import Core.utils._
import Core.{Config, CoreBundle}
import chisel3._
import chisel3.util._

class IbufPtr extends CircularQueuePtr[IbufPtr](Config.IBufSize){
  override def cloneType = (new IbufPtr).asInstanceOf[this.type]
}

class IBufferData extends CoreBundle {
  val pc   = UInt(VAddrBits.W)
  val data = UInt(16.W)
  val is_inst32 = Bool()
}

class IBuf2Decode extends CoreBundle {
  val pc   = UInt(VAddrBits.W)
  val inst = UInt(32.W)
}

class IBufferIO extends CoreBundle {
  val in   = Vec(8+1, Flipped(Valid(new IBufferData)))
  val out  = Vec(3, Decoupled(new IBuf2Decode))
  val allowEnq = Output(Bool())
  val flush = Input(Bool())
}

class IBuffer extends Module with Config with HasCircularQueuePtrHelper {
  val io = IO(new IBufferIO)

  val valid = RegInit(VecInit(Seq.fill(IBufSize)(false.B)))
  val data  = RegInit(VecInit(Seq.fill(IBufSize)(0.U.asTypeOf(new IBufferData))))

  val enqPtr = RegInit(0.U.asTypeOf(new IbufPtr))
  val deqPtr = RegInit(0.U.asTypeOf(new IbufPtr))

  val validEntries = distanceBetween(enqPtr, deqPtr)

  //Enq
  val enq_num = PopCount(io.in.map(_.valid))
  io.allowEnq := validEntries <= IBufSize.U - 9.U

  when(io.in(0).valid && io.allowEnq) {
    for (i <- 0 until 8 + 1) {
      valid(enqPtr.value + i.U) := io.in(i).valid
      data(enqPtr.value + i.U) := io.in(i).bits
    }
  }.elsewhen(!io.in(0).valid && io.allowEnq) {
    for (i <- 0 until 8) {
      valid(enqPtr.value + i.U) := io.in(i + 1).valid
      data(enqPtr.value + i.U) := io.in(i + 1).bits
    }
  }

  when(io.allowEnq){
    enqPtr := enqPtr + enq_num
  }

  //Deq
  val deq_vec = WireInit(VecInit(Seq.fill(3)(0.U.asTypeOf(new IbufPtr))))
  deq_vec(0) := deqPtr
  deq_vec(1) := deqPtr + valid(deq_vec(0).value) + (valid(deq_vec(0).value) && data(deq_vec(0).value).is_inst32)
  deq_vec(2) := deqPtr + valid(deq_vec(1).value) + (valid(deq_vec(1).value) && data(deq_vec(1).value).is_inst32)

  for(i <- 0 until 3){
    io.out(i).valid     := valid(deq_vec(i).value)
    io.out(i).bits.pc   := data(deq_vec(i).value).pc
    io.out(i).bits.inst := Cat(Mux(data(deq_vec(i).value).is_inst32, data(deq_vec(i).value+1.U).data, 0.U(16.W)), data(deq_vec(i).value).data)
  }

  val deq_num = WireInit(VecInit(Seq.fill(6)(false.B)))
  for(i <- 0 until 3){
    deq_num(2*i)   := io.out(i).fire
    deq_num(2*i+1) := io.out(i).fire && data(deq_vec(i).value).is_inst32
  }
  deqPtr := deqPtr + PopCount(deq_num)

  //flush
  when(io.flush){
    enqPtr := 0.U.asTypeOf(new IbufPtr)
    deqPtr := 0.U.asTypeOf(new IbufPtr)
    valid  := VecInit(Seq.fill(IBufSize)(false.B))
  }

}
