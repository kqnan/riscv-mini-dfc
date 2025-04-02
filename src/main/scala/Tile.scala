// See LICENSE for license details.

package mini

import chisel3.{Output, _}
import chisel3.util._
import junctions._
import freechips.rocketchip.config.Parameters
import vga.{vga, vga_pic}

//三个nasti端口,其中两个分别连接icache和dcache
class MemArbiterIO(implicit val p: Parameters) extends Bundle {
  val icache = Flipped(new NastiIO) //SLAVE
  val dcache = Flipped(new NastiIO)
  val nasti = new NastiIO
}

class MemArbiter(implicit p: Parameters) extends Module {
  val io = IO(new MemArbiterIO)

  //枚举赋值0-5
  val s_IDLE :: s_ICACHE_READ :: s_DCACHE_READ :: s_DCACHE_WRITE :: s_DCACHE_ACK :: Nil = Enum(5)
  val state = RegInit(s_IDLE)

  //dcache向nasti写入数据的过程，包括三个部分
  // Write Address    dcache向nasti写入地址
  io.nasti.aw.bits := io.dcache.aw.bits
  io.nasti.aw.valid := io.dcache.aw.valid && state === s_IDLE
  io.dcache.aw.ready := io.nasti.aw.ready && state === s_IDLE
  io.icache.aw := DontCare

  // Write Data       dcache向nasti写入数据
  io.nasti.w.bits := io.dcache.w.bits
  io.nasti.w.valid := io.dcache.w.valid && state === s_DCACHE_WRITE
  io.dcache.w.ready := io.nasti.w.ready && state === s_DCACHE_WRITE
  io.icache.w := DontCare

  // Write Ack         nasti向dcache确认信号
  io.dcache.b.bits := io.nasti.b.bits
  io.dcache.b.valid := io.nasti.b.valid && state === s_DCACHE_ACK
  io.nasti.b.ready := io.dcache.b.ready && state === s_DCACHE_ACK
  io.icache.b := DontCare


  // Read Address
  io.nasti.ar.bits := NastiReadAddressChannel(
    Mux(io.dcache.ar.valid, io.dcache.ar.bits.id, io.icache.ar.bits.id),
    Mux(io.dcache.ar.valid, io.dcache.ar.bits.addr, io.icache.ar.bits.addr),
    Mux(io.dcache.ar.valid, io.dcache.ar.bits.size, io.icache.ar.bits.size),
    Mux(io.dcache.ar.valid, io.dcache.ar.bits.len, io.icache.ar.bits.len))
  io.nasti.ar.valid := (io.icache.ar.valid || io.dcache.ar.valid) &&
    !io.nasti.aw.valid && state === s_IDLE
  io.dcache.ar.ready := io.nasti.ar.ready && !io.nasti.aw.valid && state === s_IDLE
  io.icache.ar.ready := io.dcache.ar.ready && !io.dcache.ar.valid

  // Read Data
  io.icache.r.bits := io.nasti.r.bits
  io.dcache.r.bits := io.nasti.r.bits
  io.icache.r.valid := io.nasti.r.valid && state === s_ICACHE_READ
  io.dcache.r.valid := io.nasti.r.valid && state === s_DCACHE_READ
  io.nasti.r.ready := io.icache.r.ready && state === s_ICACHE_READ ||
    io.dcache.r.ready && state === s_DCACHE_READ

  //fire:ready&&valid
  switch(state) {
    is(s_IDLE) {
      when(io.dcache.aw.fire()) {
        state := s_DCACHE_WRITE
      }.elsewhen(io.dcache.ar.fire()) {
        state := s_DCACHE_READ
      }.elsewhen(io.icache.ar.fire()) {
        state := s_ICACHE_READ
      }
    }
    is(s_ICACHE_READ) {
      when(io.nasti.r.fire() && io.nasti.r.bits.last) {
        state := s_IDLE
      }
    }
    is(s_DCACHE_READ) {
      when(io.nasti.r.fire() && io.nasti.r.bits.last) {
        state := s_IDLE
      }
    }
    is(s_DCACHE_WRITE) {
      when(io.dcache.w.fire() && io.dcache.w.bits.last) {
        state := s_DCACHE_ACK
      }
    }
    is(s_DCACHE_ACK) {
      when(io.nasti.b.fire()) {
        state := s_IDLE
      }
    }
  }
}

class TileIO(implicit val p: Parameters) extends Bundle {
  // val host = new HostIO           // 仿真
 val AXI = Flipped(new AXIIO)

  val txd = Output(UInt(1.W))   // 烧板
  val rxd = Input(UInt(1.W))    // 烧板
  val hsync = Output(UInt(1.W))
  val vsync = Output(UInt(1.W))
  val vga_rgb = Output(UInt(12.W))
  // val nasti = new NastiIO         // 烧板、操作系统仿真
}

trait TileBase extends core.BaseModule {
  def io: TileIO

  def clock: Clock

  def reset: core.Reset
}

class Tile(tileParams: Parameters) extends Module with TileBase {
  implicit val p = tileParams
  val io = IO(new TileIO)

  // DFC
  val dfc_table = Module(new DFC_Table)

  //--------------------------core---------------------------------------------------------
  //            core1
  // val core = Module(new Core(1,0x80000000L,p)) // 烧板、操作系统仿真
  val core = Module(new Core(1,0x200,p))    // dfc代码段仿真
  val icache = Module(new Cache)
  val dcache = Module(new Cache)
  val arb = Module(new MemArbiter)
  val selector = Module(new Selector)
  val immu = Module(new MMU("immu",p))
  val dmmu = Module(new MMU("dmmu",p))

  //-------------------------------------------------------------------------------------

  //--------------------devises----------------------------------------------------------
  val regmapper = Module(new RegMapper)
  //MEM
  // val mem = Module(new Memory)    // dfc代码段仿真
  val dram = Module(new DRAM)     // 烧板

  //UART
  val uartController = Module(new UartController( 100000000, 512000, p))
  val uart = Module(new Uart(100000000,512000))
  //rtc
  val rtc = Module(new RTC(10000000,p))
  //vga
  val vga= Module(new vga)
  // 操作系统仿真、dfc代码段仿真
  // val receiver = Module(new Receiver(50000000, 57600))
  // val sender = Module(new Sender(10000000, 57600))
  //-----------------------------------------------------------------------------------------

  
  // io.host <> core.io.host                // 仿真
 core.io.host.fromhost.valid := 0.U   // 烧板
 core.io.host.fromhost.bits := 0.U    // 烧板

//  core.io.icache <> icache.io.cpu
  core.io.icache <> immu.io.cpu
  immu.io.cache <> icache.io.cpu
  core.io.dcache <> selector.io.cpu


//  selector.io.dcache <> dcache.io.cpu
  selector.io.dcache <> dmmu.io.cpu
  dmmu.io.cache <> dcache.io.cpu
  selector.io.devices <> regmapper.io.selector

  regmapper.io.vga<>vga.io.cpu
  io.hsync := vga.io.hsync
  io.vsync := vga.io.vsync
  io.vga_rgb := vga.io.vga_rgb

  regmapper.io.uart <> uartController.io.cpu
  uartController.io.txChannel <> uart.io.txChannel
  uartController.io.rxChannel <> uart.io.rxChannel
  
  uart.io.rxd <> io.rxd
  uart.io.txd <> io.txd        // 烧板
  // uart.io.rxd <> sender.io.txd    // 操作系统仿真、dfc代码段仿真
  // uart.io.txd <> receiver.io.rxd  // 操作系统仿真、dfc代码段仿真

  regmapper.io.rtc <> rtc.io
  //regmapper.io.vga <> vga.io.cpu
  //mem connect
  arb.io.icache <> icache.io.nasti
  arb.io.dcache <> dcache.io.nasti

  // mem.io.nasti<> arb.io.nasti  // dfc代码段仿真
  // io.nasti <> arb.io.nasti        // 操作系统仿真

  dram.io.nasti <> arb.io.nasti  // 烧板
  io.AXI <> dram.io.AXI          // 烧板

  // ------------------DFC CONNECT  no core2-----------------------
  dfc_table.io.cpu1_w <> core.io.table_w
  dfc_table.io.cpu1_l <> core.io.table_l

  dfc_table.io.cpu2_w.data.valid := 0.U
  dfc_table.io.cpu2_w.data.bits.listenAddr := 0.U
  dfc_table.io.cpu2_w.data.bits.wPid := 0.U

  dfc_table.io.cpu2_l.data.valid := 0.U
  dfc_table.io.cpu2_l.data.bits.listenAddr := 0.U

  // 中断
  core.io.table_e <> dfc_table.io.cpu1_e
  core.io.table_f <> dfc_table.io.cpu_f

  dfc_table.io.cpu2_e.data.ready := 0.U
  // ================= DFC CONNECT END ==================

  //  if (p(Trace)) {
  //    printf("uart bits = %x uart txd = %x", tx.io.channel.bits, tx.io.txd)
  //  }
}
