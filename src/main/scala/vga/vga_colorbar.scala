package vga

import chisel3._
class vga_colorbar extends Module {


}
object emit extends App{
    (new chisel3.stage.ChiselStage).emitVerilog(new vga_colorbar())
}