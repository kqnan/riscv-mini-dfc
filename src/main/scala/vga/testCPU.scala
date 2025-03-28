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
//  val regmapper=Module(new RegMapper()((new MiniConfig).toInstance))
//  val selector = Module(new Selector()((new MiniConfig).toInstance))
//  val core= Module(new Core(1,0x200,(new MiniConfig).toInstance))
//  io.hsync:=vga_colorbar.io.hsync
//  io.vsync:=vga_colorbar.io.vsync
//  io.vga_rgb:=vga_colorbar.io.vga_rgb
//
//  regmapper.io.vga<>vga_colorbar.io.cpu
//  regmapper.io.selector<>selector.io.devices
//  regmapper.io.uart:=DontCare
//  regmapper.io.rtc:=DontCare
//  selector.io.dcache:=DontCare
//  selector.io.cpu<>core.io.dcache
//

  val base_addr=RegInit("b00011000000000000000000000000000".U)
                        //"b0001100000000000????????????????"  //????????????????"
  vga.io.cpu.abort:=false.B


}
