// See LICENSE for license details.

package mini

import chisel3._
import chisel3.util._
import junctions._
import freechips.rocketchip.config.{Parameters, Field}



class noCache(implicit val p: Parameters) extends Module with CacheParams {
  //import Chisel._ // FIXME: read enable signals for memories are broken by new chisel
  import Chisel.{SeqMem,Vec}

  val io = IO(new CacheModuleIO)
  
//   io.cpu.abort = 0.U
//   io.cpu.req.addr
//   io.cpu.req.data
//   io.cpu.req.mask
//   io.cpu.resp.data

//   io.nasti.aw.bits := NastiWriteAddressChannel(
//     0.U, tag_reg << blen.U, log2Up(nastiXDataBits/8).U, 0.U)
//   io.nasti.aw.valid := false.B
//   io.nasti.w.bits := NastiWriteDataChannel(
//     Vec.tabulate(1)(i => read((i+1)*nastiXDataBits-1, i*nastiXDataBits))(write_count),
//     None, write_wrap_out)
//   io.nasti.w.valid := false.B
//   io.nasti.b.ready := false.B 
//   io.nasti.ar.bits := NastiReadAddressChannel(
//     0.U, tag_reg << blen.U, log2Up(nastiXDataBits/8).U, 0.U)
//   io.nasti.ar.valid := false.B
//   io.nasti.r.bits.data
}
