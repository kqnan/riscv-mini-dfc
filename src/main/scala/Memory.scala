package mini

import chisel3._
import chisel3.util._
import junctions._
import freechips.rocketchip.config.Parameters
import chisel3.util.experimental.loadMemoryFromFile
import NastiConstants._

class MemoryIO(implicit val p: Parameters) extends Bundle {
  val nasti = Flipped(new NastiIO) //内存作为SLAVE
}

class Memory(implicit p: Parameters) extends Module {

  //counter 由于chisel.util中的counter接收int而nastio的len为UInt,且无法直接转换因此重新实现一个接收UInt的counter
  def BeatCounter(cond: Bool, beats: UInt): (UInt, Bool) = {
    val cnt = RegInit(0.U(8.W))
    val wrap = RegInit(false.B)
    when(cond) {
      cnt := Mux(cnt < beats, cnt + 1.U, 0.U)
    }
    wrap := Mux(cond && cnt === beats - 1.U, true.B, false.B)
    (cnt, wrap)
  }

  // 通过访存地址计算内存index
  // def index(addr: UInt) = (addr / 8.U)
  def index(addr: UInt) = (addr >> 3.U)

  val io = IO(new MemoryIO())

  // 256页，每页64bit
  val mem = Mem((512*1024*1024).toLong, UInt(64.W))  // 操作系统仿真，魔改后并不需要mem模块
  // val mem = Mem((512*1024).toLong, UInt(64.W))    // dfc代码段仿真

  //FSM状态
  val s_IDLE :: s_READ :: s_READ_DATA :: s_WRITE :: s_WRITE_DATA :: s_ACK :: Nil = Enum(6)
  //寄存器，分别存储 状态、读地址、读长度、写地址、写长度
  val state = RegInit(s_IDLE)
  val raddr = RegEnable(io.nasti.ar.bits.addr, io.nasti.ar.fire())
  val rlen = RegEnable(io.nasti.ar.bits.len, io.nasti.ar.fire())
  val waddr = RegEnable(io.nasti.aw.bits.addr, io.nasti.aw.fire())
  val wlen = RegEnable(io.nasti.aw.bits.len, io.nasti.aw.fire())

  // 从文件读取数据到mem
  // loadMemoryFromFile(mem, "/home/wen/riscv-single/kernel-bmark/kernel.hex")
  // loadMemoryFromFile(mem, "/home/wen/riscv-wzw/dfc-bmark/main.hex")          // dfc代码段仿真

//  test instruction
//  val init = RegInit(0.U)
//  when(init === 0.U) {
//    //Core1 64开始
//    mem.write(64.U, "x800001b70aa00093".U)
//    mem.write(65.U, "x0201e19380000237".U)
//    mem.write(66.U, "x0001828303026213".U)
//    mem.write(67.U, "x00120023fe028ee3".U)
//    mem.write(68.U, "x00000000ff5ff06f".U)
//
//    mem.write(512.U, "x9abcdef00000801".U)
//    mem.write(1024.U, "x000182800000001".U)
////    mem.write(70.U, "x00620023fe028ee3".U)
////    mem.write(71.U, "x00000000fe5ff06f".U)
//    init := 1.U
//  }
  //--------------------------------------------

  //counter:读写握手完成时时开始计数
  val (read_count, read_wrap_out) = BeatCounter(io.nasti.r.fire(), rlen)
  val (write_count, write_wrap_out) = BeatCounter(io.nasti.w.fire(), wlen)

  //地址传输通道ready信号：仅在IDLE状态时可接收地址传输
  io.nasti.ar.ready := state === s_IDLE
  io.nasti.aw.ready := state === s_IDLE
  //读取数据的控制信号及数据,有计数器控制突发传送的地址以及last信号·
  io.nasti.r.valid := state === s_READ
  io.nasti.r.bits := NastiReadDataChannel(0.U,
    mem.read(index(raddr) + read_count), read_wrap_out)
  //写入数据的控制信号及数据，只有在成功握手时才写入数据
  io.nasti.w.ready := state === s_WRITE
  when(io.nasti.w.fire()) {
    mem.write(index(waddr) + write_count, io.nasti.w.bits.data)
  }
  //当状态为ACK时返回响应信号，总是返回OK
  io.nasti.b.valid := state === s_ACK
  io.nasti.b.bits := NastiWriteResponseChannel(0.U, RESP_OKAY)

  //FSM
  switch(state) {
    is(s_IDLE) {
      when(io.nasti.ar.fire()) {
        state := s_READ
      }.elsewhen(io.nasti.aw.fire()) {
        state := s_WRITE
      }
    }
    is(s_READ) {
      when(read_wrap_out) {
        state := s_IDLE
      }
    }
    is(s_WRITE) {
      when(write_wrap_out) {
        state := s_ACK
      }
    }
    is(s_ACK) {
      when(io.nasti.b.fire()) {
        state := s_IDLE
      }
    }
  }
}