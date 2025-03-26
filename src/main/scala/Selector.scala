package mini

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters

class SelectorIO(implicit val p: Parameters) extends Bundle {
  val cpu = new CacheIO
  val dcache = Flipped(new CacheIO)
  val devices = Flipped(new CacheIO)
}



object Selector{
//  def memory =  BitPat("b???0????????????????????????????")
  def devices = BitPat("b0001????????????????????????????")
}

class Selector(implicit p: Parameters) extends Module {
  val io = IO(new SelectorIO())
  val addr = RegInit(0.U(32.W))

  when(io.cpu.req.fire()) {
    addr := io.cpu.req.bits.addr
  }

  //req(master to slave)
  io.dcache.req.bits.addr := io.cpu.req.bits.addr
  io.dcache.req.bits.data := io.cpu.req.bits.data
  io.dcache.req.bits.mask := io.cpu.req.bits.mask
  io.dcache.req.valid := (io.cpu.req.bits.addr =/= Selector.devices) && io.cpu.req.fire()


  io.devices.req.bits.addr := io.cpu.req.bits.addr
  io.devices.req.bits.data := io.cpu.req.bits.data
  io.devices.req.bits.mask := io.cpu.req.bits.mask
  io.devices.req.valid := (io.cpu.req.bits.addr === Selector.devices) && io.cpu.req.fire()

  io.cpu.resp.bits.data := MuxCase(io.dcache.resp.bits.data,Seq(
    (addr === Selector.devices) -> io.devices.resp.bits.data,
  ))
  io.cpu.resp.valid := MuxCase(io.dcache.resp.valid,Seq(
    (addr === Selector.devices) -> io.devices.resp.valid,
  ))
  //abort
  io.dcache.abort := io.cpu.abort
  io.devices.abort := io.cpu.abort

}