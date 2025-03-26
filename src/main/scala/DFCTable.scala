package mini

import chisel3._
import chisel3.util._
import junctions._
import freechips.rocketchip.config.{Field, Parameters}

abstract trait DFCParams {
  implicit val p: Parameters
  val xlen = p(XLEN)
  val alen = 6              // TableA，6位索引，最多64项
  val cbits = 8             // 8bits计数，最多255，与dlen一致
  val dlen = 8              // TableD，8位索引，最多256项
  val pidlen = 16           // 线程pid 16位
  val fifolen = 8           // 
}

object DFC_SIG {
  val ACT   = 1.U(2.W)  // 任务激活
  val FULL  = 2.U(2.W)  // DFC表满
  val NFULL = 3.U(2.W)  // DFC表没满，退出满状态
}

abstract class DFCBundle(implicit val p: Parameters) extends Bundle with DFCParams

// fifo是模块实现，用于fifo实例化
class WT(implicit p: Parameters) extends DFCBundle()(p){
  val listenAddr = UInt(xlen.W)
  val wPid = UInt(pidlen.W)
}

class LT(implicit p: Parameters) extends DFCBundle()(p){
  val listenAddr = UInt(xlen.W)
}

// FSM write + fifo buf
class DFC_TableA_Write_Channel(implicit p: Parameters) extends DFCBundle()(p){
  val data = Decoupled(new WT)
}

class DFC_TableA_Listen_Channel(implicit p: Parameters) extends DFCBundle()(p){
  val data = Decoupled(new LT)
}

class DFC_TableAD_Channel(implicit p: Parameters) extends DFCBundle()(p){
  val wdata = Decoupled(new Bundle{
    val listenAddr = UInt(xlen.W)
    val tableAId = UInt(alen.W)
  })
  val ldata = Decoupled(new Bundle{
    val listenAddr = UInt(xlen.W)
  })

  val wresp = Flipped(Decoupled(Bool()))
  val lresp = Flipped(Decoupled(new Bundle{
    val tableAId = UInt(alen.W)
    val finish = Bool()
  }))
}

class DFC_Expt_Channel(implicit p: Parameters) extends DFCBundle()(p){
  val data = Decoupled(new Bundle{
    val pid = UInt(pidlen.W)
  })
}

// Not Need! Will Be Deleted!
class DFC_Full_Channel(implicit p: Parameters) extends DFCBundle()(p){
  val data = Valid(new Bundle{
    val full_sig = Bool()
  })
}

class DFC_Table_IO(implicit p: Parameters) extends DFCBundle()(p) {
  val cpu1_w = Flipped(new DFC_TableA_Write_Channel)
  val cpu2_w = Flipped(new DFC_TableA_Write_Channel)
  val cpu1_l = Flipped(new DFC_TableA_Listen_Channel)
  val cpu2_l = Flipped(new DFC_TableA_Listen_Channel)

  val cpu1_e = new DFC_Expt_Channel
  val cpu2_e = new DFC_Expt_Channel
  val cpu_f = new DFC_Full_Channel
}

class DFCExptArbiterIO(implicit p: Parameters) extends DFCBundle()(p){
  val table_e = Flipped(new DFC_Expt_Channel)
  val cpu1_e = new DFC_Expt_Channel
  val cpu2_e = new DFC_Expt_Channel
}

class DFCExptArbiter(implicit val p: Parameters) extends Module  with DFCParams{
  val io = IO(new DFCExptArbiterIO)
  val flag = RegInit(false.B)

  io.cpu1_e.data.bits := io.table_e.data.bits
  io.cpu2_e.data.bits := io.table_e.data.bits
  io.table_e.data.ready := io.cpu1_e.data.ready || io.cpu2_e.data.ready
  io.cpu1_e.data.valid := io.table_e.data.valid && Mux(io.cpu1_e.data.ready && io.cpu2_e.data.ready, flag, io.cpu1_e.data.ready)
  io.cpu2_e.data.valid := io.table_e.data.valid && Mux(io.cpu1_e.data.ready && io.cpu2_e.data.ready, !flag, io.cpu2_e.data.ready)
  when(io.table_e.data.valid){
    flag := !flag
  }
}

class DFC_Table(implicit val p: Parameters) extends Module with DFCParams{
  val io = IO(new DFC_Table_IO)

  val dfc_tableA = Module(new DFC_TableA)
  val dfc_tableD = Module(new DFC_TableD)
  val dfc_arbiter = Module(new DFCArbiter)
  val cpu1_wbuf = Module(new MemFifo(new WT, fifolen))
  val cpu2_wbuf = Module(new MemFifo(new WT, fifolen))
  val cpu1_lbuf = Module(new MemFifo(new LT, fifolen))
  val cpu2_lbuf = Module(new MemFifo(new LT, fifolen))
  val expt_arbiter = Module(new DFCExptArbiter)

  // tableA、tableD
  dfc_tableA.io.tableD <> dfc_tableD.io.tableA

  // arbiter and table
  dfc_tableA.io.cpu_w <> dfc_arbiter.io.table_w
  dfc_tableA.io.cpu_l <> dfc_arbiter.io.table_l

  // arbiter and fifo
  dfc_arbiter.io.cpu1_w.data <> cpu1_wbuf.io.deq
  dfc_arbiter.io.cpu1_l.data <> cpu1_lbuf.io.deq
  dfc_arbiter.io.cpu2_w.data <> cpu2_wbuf.io.deq
  dfc_arbiter.io.cpu2_l.data <> cpu2_lbuf.io.deq

  // fifo and io
  cpu1_wbuf.io.enq <> io.cpu1_w.data
  cpu1_lbuf.io.enq <> io.cpu1_l.data
  cpu2_wbuf.io.enq <> io.cpu2_w.data
  cpu2_lbuf.io.enq <> io.cpu2_l.data

  // expt
  io.cpu1_e <> expt_arbiter.io.cpu1_e
  io.cpu2_e <> expt_arbiter.io.cpu2_e
  expt_arbiter.io.table_e <> dfc_tableA.io.cpu_expt

  io.cpu_f <> dfc_tableA.io.dfc_full
}