package vga

import chisel3._
import chisel3.util.{DecoupledIO, Enum, MuxLookup, RegEnable, is, switch}
import mini.{CacheIO, Uart}
class vga extends Module {
  val io = IO(new Bundle {
    val hsync = Output(UInt(1.W))
    val vsync = Output(UInt(1.W))
    val vga_rgb = Output(UInt(12.W))
    val cpu = new CacheIO()((new VgaConfig).toInstance)
  })
  val clkreg = withReset(!reset.asBool()) {
    RegInit(true.B)
  }
  val cnt = withReset(!reset.asBool()) {
    RegInit(0.U(2.W))
  }
  val s_IDLE :: s_WORKING :: Nil = Enum(2)
  val state = withReset(!reset.asBool()) {
    RegInit(s_IDLE)
  }
  val delay = withReset(!reset.asBool()) {
    RegInit(0.U(2.W))
  }
  val pix_clk = clkreg
  val vga_ctrl = withClockAndReset(pix_clk.asClock(), !reset.asBool()) {
    Module(new vga_ctrl)
  }
  vga_ctrl.io.cpu.bits := io.cpu.req.bits
  vga_ctrl.io.cpu.valid := io.cpu.req.valid
  io.cpu.resp.valid := false.B
  io.cpu.resp.bits.data := 0.U
  switch(state) {
    is(s_IDLE) {
      io.cpu.resp.valid := true.B
      when(io.cpu.req.valid) {
        delay := 0.U
        state := s_WORKING
      }
    }
    is(s_WORKING) {
      when(delay === 3.U) {
        state := s_IDLE
      } otherwise {
        delay := delay + 1.U
      }
    }
  }
  when(cnt === 1.U) {
    cnt := 0.U
    clkreg := !clkreg
  } otherwise {
    cnt := cnt + 1.U
  }
  io.vga_rgb := vga_ctrl.io.vga_rgb
  io.hsync := vga_ctrl.io.hsync
  io.vsync := vga_ctrl.io.vsync
}

object emitTEST extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new vga())
}