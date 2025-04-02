// ram.scala
package vga

import chisel3._
class SinglePortRAM extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(18.W))
    val dataIn = Input(UInt(12.W))
    val en = Input(Bool())
    val we = Input(Bool())
    val dataOut = Output(UInt(12.W))
  })
  val syncRAM = SyncReadMem(300*300, UInt(12.W))
  when(io.en) {
    when(io.we) {
      syncRAM.write(io.addr, io.dataIn)
      io.dataOut := DontCare
    } .otherwise {
      io.dataOut := syncRAM.read(io.addr)
    }
  } .otherwise {
    io.dataOut := DontCare
  }
}

object emitram extends App{
  (new chisel3.stage.ChiselStage).emitVerilog(new SinglePortRAM())
}