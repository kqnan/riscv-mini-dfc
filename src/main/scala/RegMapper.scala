package mini

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import vga.VgaConfig

class RegMapperIO(implicit val p: Parameters) extends Bundle {
  val selector = new CacheIO
  val uart = Flipped(new CacheIO)
  val rtc = Flipped(new CacheIO)
  val vga= Flipped(new CacheIO()((new VgaConfig).toInstance))
}

object RegMapper{
  def uart = BitPat("b0001000000000000000000000000????")
  def rtc  = BitPat("b0001000000000001000000000000????")
  def vga =  BitPat("b00011000000000??????????????????")
}

class RegMapper(implicit p: Parameters) extends Module {
  val io = IO(new RegMapperIO)
  val addr = RegEnable(io.selector.req.bits.addr,io.selector.req.fire())


  //req(master to slave)
  io.uart.req.bits := io.selector.req.bits
  io.uart.req.valid := (io.selector.req.bits.addr === RegMapper.uart) && io.selector.req.fire()
  io.rtc.req.bits := io.selector.req.bits
  io.rtc.req.valid := (io.selector.req.bits.addr === RegMapper.rtc) && io.selector.req.fire()
  io.vga.req.bits := io.selector.req.bits
  io.vga.req.valid := (io.selector.req.bits.addr === RegMapper.vga)&&io.selector.req.fire()



  //resp(slave to master)
  val respData = MuxCase(0.U,Seq(
    (addr === RegMapper.uart) -> io.uart.resp.bits.data,
    (addr === RegMapper.rtc) -> io.rtc.resp.bits.data,
    (addr === RegMapper.vga)  -> io.vga.resp.bits.data
  ))
  val respValid = MuxCase(false.B,Seq(
    (addr === RegMapper.uart) -> io.uart.resp.valid,
    (addr === RegMapper.rtc) -> io.rtc.resp.valid,
    (addr === RegMapper.vga) -> io.vga.resp.valid
  ))





  io.selector.resp.bits.data := respData
  io.selector.resp.valid := respValid

  //abort
  io.uart.abort := io.selector.abort
  io.rtc.abort := io.selector.abort
  io.vga.abort := io.selector.abort
}