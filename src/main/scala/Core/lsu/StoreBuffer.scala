package Core.lsu

import Core.utils.{HasCircularQueuePtrHelper, ParallelPriorityEncoder}
import Core.{Config, CoreBundle}
import chisel3._
import chisel3.util._

class StoreBufferData extends CoreBundle {
  val rob_idx = new ROBPtr
  val mask  = UInt((XLEN/8).W)

  val vaddr = UInt(VAddrBits.W)
  val paddr = UInt(PAddrBits.W)
  val data  = UInt(XLEN.W)

}

class StoreBufferIO extends CoreBundle {
  val in  = Flipped(Decoupled(new StoreQueueData))
  val out = new Store_Cache
  val fw_check = new ForwardCheck
}

class StoreBuffer extends Module with Config with HasCircularQueuePtrHelper {
  val io = IO(new StoreBufferIO)

  val valid = RegInit(VecInit(Seq.fill(StoreBufferSize)(false.B)))
  val data  = RegInit(VecInit(Seq.fill(StoreBufferSize)(0.U.asTypeOf(new StoreBufferData))))
  val cache_wait_cnt = RegInit(VecInit(Seq.fill(StoreBufferSize)(0.U(4.W))))

  val full = valid.asUInt.andR

  val enqueueSelect = ParallelPriorityEncoder(valid.map(!_))
  val deqValid = valid.zip(cache_wait_cnt).map(v_num => v_num._1 && (v_num._2 === 0.U))
  val dequeueSelect = ParallelPriorityEncoder(deqValid)

  //enqueue
  when(io.in.valid && !full){
    valid(enqueueSelect) := true.B
    data(enqueueSelect).mask  := io.in.bits.mask
    data(enqueueSelect).vaddr := io.in.bits.vaddr
    data(enqueueSelect).paddr := io.in.bits.paddr
    data(enqueueSelect).data  := io.in.bits.data
    data(enqueueSelect).rob_idx := io.in.bits.rob_idx
    cache_wait_cnt(enqueueSelect) := 0.U
  }
  io.in.ready := !full

  //dequeue
  val addr_match = WireInit(VecInit(Seq.fill(StoreBufferSize)(false.B)))
  for(i <- 0 until StoreBufferSize){
    addr_match(i) := valid(i) && data(dequeueSelect).paddr === data(i).paddr && isAfter(data(dequeueSelect).rob_idx,data(i).rob_idx)
  }
  val dequeueReady = valid(dequeueSelect) && cache_wait_cnt(dequeueSelect) === 0.U
  io.out.valid := dequeueReady && !addr_match.asUInt.orR
  io.out.vaddr := data(dequeueSelect).vaddr
  io.out.paddr := data(dequeueSelect).paddr
  io.out.mask  := data(dequeueSelect).mask
  io.out.data  := data(dequeueSelect).data
  when(dequeueReady && !io.out.miss && !addr_match.asUInt.orR){
    valid(dequeueSelect) := false.B
  }.elsewhen(dequeueReady && (io.out.miss || addr_match.asUInt.orR)){
    cache_wait_cnt(dequeueSelect) := 15.U
  }

  for(i <- 0 until StoreBufferSize){
    cache_wait_cnt(i) := Mux(cache_wait_cnt(i) === 0.U, 0.U, cache_wait_cnt(i) - 1.U)
  }

  //forward check
  val need_fw = WireInit(VecInit(Seq.fill(StoreBufferSize)(false.B)))
  for(i <- 0 until StoreBufferSize){
    need_fw(i) := valid(i) && data(i).paddr === io.fw_check.paddr && (data(i).mask ^ io.fw_check.mask).orR
  }

  val fw_sel = ParallelPriorityEncoder(need_fw)
  io.fw_check.fw_inv  := PopCount(need_fw) > 1.U
  io.fw_check.fw_data := data(fw_sel).data
  io.fw_check.fw_mask := data(fw_sel).mask ^ io.fw_check.mask
}
