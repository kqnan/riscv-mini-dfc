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
        val buffer= Mem(640*480,UInt(12.W))

        val x=io.cpu.req.bits.addr(18,9)
        val y=io.cpu.req.bits.addr(8,0)
        val wdata=io.cpu.req.bits.data

        //有可能没写入
        when(io.cpu.req.fire()){
            buffer.write(640.U*y+x,wdata)
        }

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

        io.vga_rgb:=buffer.read(vga_ctrl.io.pix_y*640.U+vga_ctrl.io.pix_x)
    // io.vga_rgb:=vga_ctrl.io.vga_rgb
        io.hsync:=vga_ctrl.io.hsync
        io.vsync:=vga_ctrl.io.vsync
        vga_ctrl.io.pix_data:=vga_pic.io.pix_data
        vga_pic.io.pix_x:=vga_ctrl.io.pix_x
        vga_pic.io.pix_y:=vga_ctrl.io.pix_y
        vga_pic.io.cpu<>io.cpu
}
object emitTEST extends App{
    (new chisel3.stage.ChiselStage).emitVerilog(new vga_colorbar())
}