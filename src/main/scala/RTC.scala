package mini

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters

object RTC{
  val time  = "x10001000".U
  val timeh = "x10001004".U
}

class RTC(frequency : Int,implicit val p: Parameters) extends Module{
  val io = IO(new CacheIO)

  val s_IDLE :: s_TIME :: s_TIMEH :: Nil = Enum(3)

  val time = RegInit(0.U(32.W))
  val timeh = RegInit(0.U(32.W))
  val state = RegInit(s_IDLE)
  val addr = RegInit(0.U(32.W))
  val mask = RegInit(0.U(4.W))
  val data = RegInit(0.U(32.W))

  io.resp.bits.data := 0.U
  io.resp.valid := false.B

  //counter
  val CNT_MAX = (frequency - 1).U
  val cntReg = RegInit(0.U(32.W))

  cntReg := cntReg + 1.U
  when(cntReg === CNT_MAX){
    cntReg := 0.U
    time := time + 1.U
//    printf("timeh = %x ,time = %x\n",timeh,time)
    when(time.andR) { timeh := timeh + 1.U }
  }

  switch(state){
    is(s_IDLE){
      addr := 0.U
      io.resp.valid := true.B
      when(io.req.fire()){
        addr := io.req.bits.addr
        mask := io.req.bits.mask
        data := io.req.bits.data
        state := MuxCase(s_IDLE,Seq(
          (io.req.bits.addr === BitPat(RTC.time))-> s_TIME,
          (io.req.bits.addr === BitPat(RTC.timeh))-> s_TIMEH
        ))
      }
    }
    is(s_TIME){
        when(!mask.orR()){
          io.resp.bits.data  := time
          io.resp.valid := true.B
        }.otherwise{
          time := data
        }
      state := s_IDLE
    }
    is(s_TIMEH){
      when(!mask.orR()){
        io.resp.bits.data := timeh
        io.resp.valid := true.B
      }.otherwise{
        timeh := data
      }
      state := s_IDLE
    }
  }
}