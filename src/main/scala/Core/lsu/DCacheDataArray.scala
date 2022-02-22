package Core.lsu
import Core.{Config, CoreBundle}
import chisel3._
import chisel3.util._

class ReadPort extends CoreBundle {
  val addr = Input(UInt(log2Up(DCacheSets).W))
  //one cycle delay
  val data = Output(UInt(CacheLineBits.W))
}

class WritePort extends CoreBundle {
  val wen  = Input(Bool())
  val addr = Input(UInt(log2Up(DCacheSets).W))
  val data = Input(UInt(CacheLineBits.W))
  val mask = Input(UInt(CacheLineSize.W))
}


class DCacheDataArray extends Module with  Config {
  val io = IO(new Bundle{
    val read  = Vec(2, new ReadPort)
    val write = Vec(2, new WritePort)
  })

  val data = RegInit(VecInit(Seq.fill(DCacheSets)(0.U(CacheLineBits.W))))
  //write
  for(i <- 0 until 2){
    val WriteData = WireInit(0.U(CacheLineBits.W))
    for(j <- 0 until CacheLineSize){
      when(io.write(i).wen && io.write(i).mask(j)){
        WriteData(j*8+7,j*8) := io.write(i).data(j*8+7,j*8)
      }.otherwise{
        WriteData(j*8+7,j*8) := data(io.write(i).addr)(j*8+7,j*8)
      }
    }
    data(io.write(i).addr) := WriteData
  }

  assert(!(io.write(0).wen && io.write(1).wen && io.write(0).addr === io.write(1).addr))

  //read && write(0) to read(0) forward
  val read0_addr_reg = RegNext(io.read(0).addr)
  when(io.write(0).wen && io.write(0).addr === read0_addr_reg){
    val ReadData0 = WireInit(0.U(CacheLineBits.W))
    for(j <- 0 until CacheLineSize){
      when(io.write(0).mask(j)){
        ReadData0(j*8+7,j*8) := io.write(0).data(j*8+7,j*8)
      }.otherwise{
        ReadData0(j*8+7,j*8) := data(read0_addr_reg)(j*8+7,j*8)
      }
    }
    io.read(0).data := ReadData0
  }

  io.read(1).data := data(RegNext(io.read(1).addr))
}
