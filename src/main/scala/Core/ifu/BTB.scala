package Core.ifu
import Core.{Config, CoreBundle}
import chisel3._
import chisel3.util._

class BTBUpdate extends CoreBundle {
  val btb_index = UInt(10.W)
  val btb_tag   = UInt(10.W)
  val btb_data  = UInt(20.W)
}

class BTBIO extends CoreBundle {
  val pc             = Input(UInt(VAddrBits.W))
  val btb_target     = Vec(4,Valid(UInt(20.W)))
  val btb_update     = Flipped(Valid(new BTBUpdate))
  val ib_btb_mispred = Input(Bool())
}

class BTB extends Module with Config {
  val io = IO(new BTBIO)

  //TODO: SRAM
  val btb_valid = RegInit(VecInit(Seq.fill(1024)(VecInit(Seq.fill(4)(false.B)))))
  val btb_tag   = RegInit(VecInit(Seq.fill(1024)(VecInit(Seq.fill(4)(0.U(10.W))))))
  val btb_data  = RegInit(VecInit(Seq.fill(1024)(VecInit(Seq.fill(4)(0.U(20.W))))))

  val if_index = io.pc(13,4)
  val if_tag   = WireInit(VecInit(Seq.fill(4)(0.U(10.W))))
  for(i <- 0 until 4){
    if_tag(i) := Cat(io.pc(20,14), 0.U(3.W)) + (i.U << 1.U)
  }

  for(i <- 0 until 4){
    val tag   = RegNext(if_tag(i))
    val index = RegNext(if_index)
    io.btb_target(i).bits  := btb_data(index)(tag(2,1))
    io.btb_target(i).valid := btb_valid(index)(tag(2,1)) && btb_tag(index)(tag(2,1)) === tag
  }

  //btb_update
  val update_info  = RegInit(0.U.asTypeOf(new BTBUpdate))
  val update_valid = RegInit(false.B)
  when(io.btb_update.valid){
    update_info := io.btb_update.bits
    update_info := true.B
  }

  when(io.ib_btb_mispred && update_valid){
    btb_valid(update_info.btb_index)(update_info.btb_tag(2,1)) := true.B
    btb_tag(update_info.btb_index)(update_info.btb_tag(2,1))   := update_info.btb_tag
    btb_data(update_info.btb_index)(update_info.btb_tag(2,1))  := update_info.btb_data
    update_info := false.B
  }
}
