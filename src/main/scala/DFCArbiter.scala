package mini

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import junctions._

class DFCArbiterIO(implicit p: Parameters) extends DFCBundle()(p){
  val cpu1_w = Flipped(new DFC_TableA_Write_Channel)
  val cpu2_w = Flipped(new DFC_TableA_Write_Channel)
  val cpu1_l = Flipped(new DFC_TableA_Listen_Channel)
  val cpu2_l = Flipped(new DFC_TableA_Listen_Channel)
  val table_w = new DFC_TableA_Write_Channel
  val table_l = new DFC_TableA_Listen_Channel
}

// 由于是无反馈写入，无反馈监听， 所以不用FSM
class DFCArbiter(implicit val p: Parameters) extends Module  with DFCParams{
  val io = IO(new DFCArbiterIO)

  // write table
  io.table_w.data.valid := (io.cpu1_w.data.valid || io.cpu2_w.data.valid)
  io.cpu1_w.data.ready := io.table_w.data.ready
  io.cpu2_w.data.ready := io.cpu1_w.data.ready && !io.cpu1_w.data.valid
  io.table_w.data.bits.listenAddr := Mux(io.cpu1_w.data.valid, 
                                        io.cpu1_w.data.bits.listenAddr, 
                                        io.cpu2_w.data.bits.listenAddr)
  io.table_w.data.bits.wPid := Mux(io.cpu1_w.data.valid, 
                                  io.cpu1_w.data.bits.wPid, 
                                  io.cpu2_w.data.bits.wPid)
  
  // listen table
  io.table_l.data.valid := (io.cpu1_l.data.valid || io.cpu2_l.data.valid) && !io.table_w.data.valid
  io.cpu1_l.data.ready := io.table_l.data.ready && !io.table_w.data.valid
  io.cpu2_l.data.ready := io.cpu1_l.data.ready && !io.cpu1_l.data.valid
  io.table_l.data.bits.listenAddr := Mux(io.cpu1_l.data.valid, 
                                        io.cpu1_l.data.bits.listenAddr, 
                                        io.cpu2_l.data.bits.listenAddr)
}
