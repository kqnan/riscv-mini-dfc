package vga

import chisel3._
import chisel3.util.RegEnable
import mini.{CacheIO, Core, MiniConfig, RegMapper, Selector}
class testCPU extends Module{

  val io = IO(new Bundle{
    val hsync=Output(UInt(1.W))
    val vsync=Output(UInt(1.W))
    val vga_rgb=Output(UInt(12.W))
  })
  val vga=Module(new vga_colorbar)
  val x=withReset(!reset.asBool()){RegInit(0.U(10.W))}
  val y=withReset(!reset.asBool()){RegInit(0.U(9.W))}

  val base_addr="b0001100000000000".U##x##y
  vga.io.cpu.req.bits.mask:=0XFFF.U(12.W)
  vga.io.cpu.req.bits.addr:=base_addr
  vga.io.cpu.req.bits.data:=x
  io.hsync:=vga.io.hsync
  io.vsync:=vga.io.vsync
  io.vga_rgb:=vga.io.vga_rgb
  when(x===639.U){
    y:=0.U
    when(y===479.U){
      y:=0.U
    }.otherwise{
      y:=y+1.U
    }
  }.otherwise{
    x:=x+1.U
  }
  vga.io.cpu.req.valid:=true.B
  vga.io.cpu.abort:=false.B
}
object emit extends App{
  (new chisel3.stage.ChiselStage).emitVerilog(new testCPU())
}