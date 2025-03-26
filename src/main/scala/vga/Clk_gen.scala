package vga

import chisel3._
class Clk_gen extends Module {
  val io=IO{new Bundle{
    val Clk_in=Input(Bool())
    val areset=Input(Bool())
    val locked=Output(Bool())
    val Clk_out=Output(Bool())
  }}
  val cnt=RegInit(0.U(2.W))
  io.locked:=true.B
  when(cnt===3.U){
    io.Clk_out:=true.B
  }.elsewhen(io.areset===false.B){
    cnt:=0.U
    io.Clk_out:=false.B
  }.otherwise{
      cnt:=cnt+1.U
    io.Clk_out:=false.B
  }
}
object emits extends App{
  (new chisel3.stage.ChiselStage).emitVerilog(new Clk_gen())
}