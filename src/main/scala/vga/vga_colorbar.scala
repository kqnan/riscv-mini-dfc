package vga

import chisel3._
import chisel3.util.RegEnable
import mini.CacheIO
class vga_colorbar extends Module {

    val io = IO(new Bundle{
        val hsync=Output(UInt(1.W))
        val vsync=Output(UInt(1.W))
        val vga_rgb=Output(UInt(12.W))
        val cpu=new CacheIO()((new VgaConfig).toInstance)
    })
        val clkreg=withReset(!reset.asBool()){RegInit(true.B)}
        val cnt=withReset(!reset.asBool()){RegInit(0.U(2.W))}
        when(cnt===1.U){
            cnt:=0.U
            clkreg :=  !clkreg
        }otherwise{
            cnt:=cnt+1.U
        }
        val pix_clk=clkreg
        val vga_ctrl=withClockAndReset(pix_clk.asClock(),!reset.asBool()){Module(new vga_ctrl)}
        val vga_pic=withClockAndReset(pix_clk.asClock(),!reset.asBool()){Module(new vga_pic)}
        io.vga_rgb:=vga_ctrl.io.vga_rgb
        io.hsync:=vga_ctrl.io.hsync
        io.vsync:=vga_ctrl.io. vsync
        vga_ctrl.io.pix_data:=vga_pic.io.pix_data
        vga_pic.io.pix_x:=vga_ctrl.io.pix_x
        vga_pic.io.pix_y:=vga_ctrl.io.pix_y
        vga_pic.io.cpu<>io.cpu
}
object emit extends App{
    (new chisel3.stage.ChiselStage).emitVerilog(new vga_colorbar())
}