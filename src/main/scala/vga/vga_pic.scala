package vga

import chisel3._
import chisel3.util.{Enum, MuxLookup, is, switch}
import freechips.rocketchip.config.{Config, Parameters}
import junctions.{NastiKey, NastiParameters}
import mini.{ALUArea, BrCondArea, BuildALU, BuildBrCond, BuildImmGen, CacheBlockBytes, CacheIO, ImmGenWire, NSets, NWays, Trace, Uart, XLEN}

class VgaConfig extends Config((site, here, up) => {
  // data len
  case XLEN => 19
}
)
class vga_pic extends Module{
  val io = IO(new Bundle{


      val pix_x=Input(UInt(10.W))
      val pix_y=Input(UInt(10.W))
      val pix_data=Output(UInt(12.W))
      val cpu = new CacheIO()((new VgaConfig).toInstance)
  })
  val buffer= Mem(640*480,UInt(12.W))
  val H_VALID=640.U;val V_VALID=480.U;
  val RED=0XF00.U;val ORANGE=0XFB0.U;val YELLOW=0xFF0.U;
  val GREEN=0x0F0.U;val CYAN=0X0FF.U;val BLUE=0X00F.U
  val PURPPLE=0XF0F.U;val BLACK=0.U;val WHITE=0XFFF.U
  val GRAY=0XDB5.U

  val s_IDLE :: s_READ_READ_STATE :: s_READ_WRITE_STATE :: s_READ_DATA :: s_WRITE_DATA :: Nil = Enum(5)
  val state = RegInit(s_IDLE)
  val x=io.cpu.req.bits.addr(18,9)
  val y=io.cpu.req.bits.addr(8,0)
  val wdata=io.cpu.req.bits.data

  io.cpu.resp.bits.data:=0.U
  io.cpu.resp.valid:=true.B
  when(io.cpu.req.fire()){
    buffer.write(640.U*y+x,wdata)
  }

  //withClockAndReset(io.vga_clk,io.sys_rst_n){
//    when(io.sys_rst_n===false.B){
//      io.pix_data:=RED
//    }otherwise {
//      io.pix_data:=buffer.read(io.pix_y*640.U+io.pix_x)
//    }
    io.pix_data:=buffer.read(io.pix_y*640.U+io.pix_x)
//    when(io.pix_x>=0.U && io.pix_x<((640.U/10.U)*1.U)){
//      io.pix_data:=RED
//    }.elsewhen(io.pix_x>=((640.U/10.U)*1.U)&&io.pix_x<((640.U/10.U)*2.U)){
//      io.pix_data:=ORANGE
//      // io.pix_data:=GREEN
//    }.elsewhen(io.pix_x>=((640.U/10.U)*2.U)&&io.pix_x<((640.U/10.U)*3.U)){
//      io.pix_data:=YELLOW
//      // io.pix_data:=BLUE
//    }.elsewhen(io.pix_x>=((640.U/10.U)*3.U)&&io.pix_x<((640.U/10.U)*4.U)){
//      io.pix_data:=GREEN
//    }.elsewhen(io.pix_x>=((640.U/10.U)*4.U)&&io.pix_x<((640.U/10.U)*5.U)){
//      io.pix_data:=CYAN
//      //io.pix_data:=GREEN
//    }.elsewhen(io.pix_x>=((640.U/10.U)*5.U)&&io.pix_x<((640.U/10.U)*6.U)){
//      //io.pix_data:=BLUE
//      io.pix_data:=BLUE
//    }.elsewhen(io.pix_x>=((640.U/10.U)*6.U)&&io.pix_x<((640.U/10.U)*7.U)){
//      io.pix_data:=PURPPLE
//      //io.pix_data:=RED
//    }.elsewhen(io.pix_x>=((640.U/10.U)*7.U)&&io.pix_x<((640.U/10.U)*8.U)){
//      io.pix_data:=BLACK
//      //io.pix_data:=GREEN
//    }.elsewhen(io.pix_x>=((640.U/10.U)*8.U)&&io.pix_x<((640.U/10.U)*9.U)){
//      io.pix_data:=WHITE
//      //io.pix_data:=BLUE
//    }.elsewhen(io.pix_x>=((640.U/10.U)*9.U)&&io.pix_x<((640.U/10.U)*10.U)){
//      io.pix_data:=GRAY
//    }.otherwise{
//      io.pix_data:=BLACK
//    }

  //}
}
object emitt extends App{
  (new chisel3.stage.ChiselStage).emitVerilog(new vga_pic())
  (new chisel3.stage.ChiselStage).emitVerilog(new vga_colorbar())
}