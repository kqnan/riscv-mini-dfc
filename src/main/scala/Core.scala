// See LICENSE for license details.

package mini

import chisel3._
import chisel3.util.Valid
import freechips.rocketchip.config.{Parameters, Field}

case object XLEN extends Field[Int]
case object Trace extends Field[Boolean]
case object BuildALU extends Field[Parameters => ALU]
case object BuildImmGen extends Field[Parameters => ImmGen]
case object BuildBrCond extends Field[Parameters => BrCond]

abstract trait CoreParams {
  implicit val p: Parameters
  val xlen = p(XLEN)
}

abstract class CoreBundle(implicit val p: Parameters) extends Bundle with CoreParams

class HostIO(implicit p: Parameters) extends CoreBundle()(p) {
  val fromhost = Flipped(Valid(UInt(xlen.W)))
  val tohost   = Output(UInt(xlen.W))
}

class CoreIO(implicit p: Parameters) extends CoreBundle()(p) {
  val host = new HostIO
  val icache = Flipped((new CacheIO))
  val dcache = Flipped((new CacheIO))

  // DFC
  val table_w = new DFC_TableA_Write_Channel
  val table_l = new DFC_TableA_Listen_Channel
  val table_e = Flipped(new DFC_Expt_Channel)
  val table_f = Flipped(new DFC_Full_Channel)
}

class Core(coreNum : Int , pc_start: Long ,implicit val p: Parameters) extends Module with CoreParams {
  val io = IO(new CoreIO)
  val dpath = Module(new Datapath(coreNum,pc_start,p))
  val ctrl  = Module(new Control)

  io.host <> dpath.io.host
  dpath.io.icache <> io.icache
  dpath.io.dcache <> io.dcache
  dpath.io.ctrl <> ctrl.io

  // DFC
  io.table_w <> dpath.io.table_w
  io.table_l <> dpath.io.table_l
  io.table_e <> dpath.io.table_e
  io.table_f <> dpath.io.table_f
}
