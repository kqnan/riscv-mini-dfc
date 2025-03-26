package mini

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import junctions.NastiIO

class AXIIO extends Bundle {
    val s_axi_awid = Input(UInt(4.W))         // input wire [3 : 0] s_axi_awid
    val s_axi_awaddr = Input(UInt(32.W))      // input wire [31 : 0] s_axi_awaddr
    val s_axi_awlen = Input(UInt(8.W))        // input wire [7 : 0] s_axi_awlen
    val s_axi_awsize = Input(UInt(3.W))       // input wire [2 : 0] s_axi_awsize
    val s_axi_awburst = Input(UInt(2.W))       // input wire [1 : 0] s_axi_awburs
    val s_axi_awvalid = Input(Bool())         // input wire s_axi_awvalid
    val s_axi_awready = Output(Bool())        // output wire s_axi_awready
    val s_axi_wdata = Input(UInt(32.W))       // input wire [63 : 0] s_axi_wdata
    val s_axi_wstrb = Input(UInt(8.W))        // input wire [7 : 0] s_axi_wstrb
    val s_axi_wlast = Input(Bool())           // input wire s_axi_wlast
    val s_axi_wvalid = Input(Bool())          // input wire s_axi_wvalid
    val s_axi_wready = Output(Bool())         // output wire s_axi_wready
    val s_axi_bid = Output(UInt(4.W))         // output wire [3 : 0] s_axi_bid
    val s_axi_bresp = Output(UInt(2.W))       // output wire [1 : 0] s_axi_bresp
    val s_axi_bvalid = Output(Bool())         // output wire s_axi_bvalid
    val s_axi_bready = Input(Bool())          // input wire s_axi_bready
    val s_axi_arid = Input(UInt(4.W))         // input wire [3 : 0] s_axi_arid
    val s_axi_araddr = Input(UInt(32.W))      // input wire [31 : 0] s_axi_araddr
    val s_axi_arlen = Input(UInt(8.W))        // input wire [7 : 0] s_axi_arlen
    val s_axi_arsize = Input(UInt(3.W))       // input wire [2 : 0] s_axi_arsize
    val s_axi_arburst = Input(UInt(2.W))       // input wire [1 : 0] s_axi_arburs
    val s_axi_arvalid = Input(Bool())         // input wire s_axi_arvalid
    val s_axi_arready = Output(Bool())        // output wire s_axi_arready
    val s_axi_rid = Output(UInt(4.W))         // output wire [3 : 0] s_axi_rid
    val s_axi_rdata = Output(UInt(32.W))       // output wire [63 : 0] s_axi_rdata
    val s_axi_rresp = Output(UInt(2.W))       // output wire [1 : 0] s_axi_rresp
    val s_axi_rlast = Output(Bool())          // output wire s_axi_rlast
    val s_axi_rvalid = Output(Bool())         // output wire s_axi_rvalid
    val s_axi_rready = Input(Bool())          // input wire s_axi_rready
}

class DRAMIO(implicit val p: Parameters) extends Bundle {
  val nasti = Flipped(new NastiIO)
  val AXI = Flipped(new AXIIO)
}

class DRAM (implicit val p: Parameters) extends Module{
  val io = IO(new DRAMIO())
  io.AXI.s_axi_awid := io.nasti.aw.bits.id
  io.AXI.s_axi_awaddr := io.nasti.aw.bits.addr
  io.AXI.s_axi_awlen := io.nasti.aw.bits.len
  io.AXI.s_axi_awsize := io.nasti.aw.bits.size
  io.AXI.s_axi_awburst := io.nasti.aw.bits.burst
  io.AXI.s_axi_awvalid := io.nasti.aw.valid
  io.nasti.aw.ready := io.AXI.s_axi_awready
  io.AXI.s_axi_wdata := io.nasti.w.bits.data
  io.AXI.s_axi_wstrb := io.nasti.w.bits.strb
  io.AXI.s_axi_wlast := io.nasti.w.bits.last
  io.AXI.s_axi_wvalid := io.nasti.w.valid
  io.nasti.w.ready := io.AXI.s_axi_wready
  io.nasti.b.bits.id := io.AXI.s_axi_bid
  io.nasti.b.bits.resp := io.AXI.s_axi_bresp
  io.nasti.b.valid := io.AXI.s_axi_bvalid
  io.AXI.s_axi_bready := io.nasti.b.ready
  io.AXI.s_axi_arid := io.nasti.ar.bits.id
  io.AXI.s_axi_araddr := io.nasti.ar.bits.addr
  io.AXI.s_axi_arlen := io.nasti.ar.bits.len
  io.AXI.s_axi_arsize := io.nasti.ar.bits.size
  io.AXI.s_axi_arburst := io.nasti.ar.bits.burst
  io.AXI.s_axi_arvalid := io.nasti.ar.valid
  io.nasti.ar.ready := io.AXI.s_axi_arready
  io.nasti.r.bits.id := io.AXI.s_axi_rid
  io.nasti.r.bits.data := io.AXI.s_axi_rdata
  io.nasti.r.bits.resp := io.AXI.s_axi_rresp
  io.nasti.r.bits.last := io.AXI.s_axi_rlast
  io.nasti.r.valid := io.AXI.s_axi_rvalid
  io.AXI.s_axi_rready := io.nasti.r.ready
  //unused signal
  io.nasti.b.bits.user := 0.U
  io.nasti.r.bits.user := 0.U
}
