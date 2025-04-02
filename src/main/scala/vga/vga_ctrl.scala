package vga

import Chisel.Valid
import chisel3._
import chisel3.util.{Enum, is, switch}
import mini.{CacheIO, CacheReq}

class vga_ctrl extends Module {
  val io = IO(new Bundle {
    val hsync = Output(UInt(1.W))
    val vsync = Output(UInt(1.W))
    val rgb_valid = Output(UInt(1.W))
    val cpu = Flipped(Valid(new CacheReq()((new VgaConfig).toInstance)))
    val vga_rgb = Output(UInt(12.W))
  })
  val mem = Module(new SinglePortRAM)
  val H_SYNC = 96.U;
  val H_BACK = 40.U;
  val H_LEFT = 8.U;
  val H_VALID = 640.U;
  val H_RIGHT = 8.U;
  val H_FRONT = 8.U;
  val H_TOTAL = 800.U;
  val V_SYNC = 2.U;
  val V_BACK = 25.U;
  val V_TOP = 8.U;
  val V_VALID = 480.U;
  val V_BOTTOM = 8.U;
  val V_FRONT = 2.U;
  val V_TOTAL = 525.U;

  val cnt_h = RegInit(0.U(10.W))
  val cnt_v = RegInit(0.U(10.W))
  val rgb_valid = Mux(cnt_h < H_VALID && cnt_v < V_VALID, true.B, false.B)

  val x = io.cpu.bits.addr(17, 9)
  val y = io.cpu.bits.addr(8, 0)

  //  mem.io.addr:=Mux(io.cpu.req.valid,y*300.U+x,Mux(rgb_valid,cnt_v*300.U+cnt_h,0.U))
  when(io.cpu.valid) {
    mem.io.addr := y * 300.U + x
    //  h=799,0~638    v=0~478
  }.elsewhen(cnt_h >= 0.U && cnt_h <= 298.U && cnt_v < 300.U) {
    mem.io.addr := cnt_v * 300.U + cnt_h + 1.U
  }.elsewhen(cnt_h === 799.U && cnt_v < 300.U) {
    mem.io.addr := (cnt_v + 1.U) * 300.U
  }.elsewhen(cnt_h === 799.U && cnt_v === 523.U) {
    mem.io.addr := 0.U
  }.otherwise {
    mem.io.addr := 0.U
  }
  when(io.cpu.valid) {
    mem.io.we := true.B
  } otherwise {
    mem.io.we := false.B
  }
  mem.io.en := true.B
  mem.io.dataIn := io.cpu.bits.data


  io.vga_rgb := Mux(cnt_h < 300.U && cnt_v < 300.U, mem.io.dataOut, 0.U)

  when(cnt_h === (H_TOTAL - 1.U)) {
    cnt_h := 0.U
  }.otherwise {
    cnt_h := cnt_h + 1.U
  }

  //}
  //  withClockAndReset(io.vga_clk,io.sys_rst_n){
  when((cnt_v === (V_TOTAL - 1.U)) && (cnt_h === (H_TOTAL - 1.U))) {
    cnt_v := 0.U
  }.elsewhen(cnt_h === (H_TOTAL - 1.U)) {
    cnt_v := cnt_v + 1.U
  }.otherwise {
    cnt_v := cnt_v
  }

  io.hsync := Mux(cnt_h >= H_VALID + H_RIGHT + H_FRONT && cnt_h < H_VALID + H_RIGHT + H_FRONT + H_SYNC, 1.U, 0.U)
  io.vsync := Mux(cnt_v >= V_VALID + V_FRONT + V_BOTTOM && cnt_v < V_VALID + V_FRONT + V_BOTTOM + V_SYNC, 1.U, 0.U)

  io.rgb_valid := rgb_valid
}

object emitsss extends App {
  (new chisel3.stage.ChiselStage).emitVerilog(new vga_ctrl())
  (new chisel3.stage.ChiselStage).emitVerilog(new vga_pic())
}