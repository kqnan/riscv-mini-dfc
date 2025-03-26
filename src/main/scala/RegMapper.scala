package mini

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters

class RegMapperIO(implicit val p: Parameters) extends Bundle {
  val selector = new CacheIO
  val uart = Flipped(new CacheIO)
  val rtc = Flipped(new CacheIO)
}

object RegMapper{
  def uart = BitPat("b0001000000000000000000000000????")
  def rtc  = BitPat("b0001000000000001000000000000????")
  def vga =  BitPat("b0001100000000000????????????????")
}

class RegMapper(implicit p: Parameters) extends Module {
  val io = IO(new RegMapperIO)
  val addr = RegEnable(io.selector.req.bits.addr,io.selector.req.fire())


  //req(master to slave)
  io.uart.req.bits := io.selector.req.bits
  io.uart.req.valid := (io.selector.req.bits.addr === RegMapper.uart) && io.selector.req.fire()
  io.rtc.req.bits := io.selector.req.bits
  io.rtc.req.valid := (io.selector.req.bits.addr === RegMapper.rtc) && io.selector.req.fire()

  //resp(slave to master)
  val respData = MuxCase(0.U,Seq(
    (addr === RegMapper.uart) -> io.uart.resp.bits.data,
    (addr === RegMapper.rtc) -> io.rtc.resp.bits.data,
  ))
  val respValid = MuxCase(false.B,Seq(
    (addr === RegMapper.uart) -> io.uart.resp.valid,
    (addr === RegMapper.rtc) -> io.rtc.resp.valid,
  ))

  io.selector.resp.bits.data := respData
  io.selector.resp.valid := respValid

  //abort
  io.uart.abort := io.selector.abort
  io.rtc.abort := io.selector.abort
}