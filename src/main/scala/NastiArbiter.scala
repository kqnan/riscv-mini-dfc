package mini

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters
import junctions._

class NastiArbiterIO(implicit val p: Parameters) extends Bundle {
  val master1 = Flipped(new NastiIO) //SLAVE
  val master2 = Flipped(new NastiIO)
  val slave = new NastiIO
}

class NastiArbiter(implicit p: Parameters) extends Module{
  val io = IO(new NastiArbiterIO)

  val s_IDLE :: s_CPU1_READ::s_CPU1_WRITE :: s_CPU1_ACK :: s_CPU2_READ :: s_CPU2_WRITE :: s_CPU2_ACK :: Nil = Enum(7)
  val state = RegInit(s_IDLE)

  //Write Address
  io.slave.aw.bits := NastiWriteAddressChannel(
    Mux(io.master1.aw.valid,io.master1.aw.bits.id,io.master2.aw.bits.id),
    Mux(io.master1.aw.valid,io.master1.aw.bits.addr,io.master2.aw.bits.addr),
    Mux(io.master1.aw.valid,io.master1.aw.bits.size,io.master2.aw.bits.size),
    Mux(io.master1.aw.valid,io.master1.aw.bits.len,io.master2.aw.bits.len))
  io.slave.aw.valid := (io.master1.aw.valid || io.master2.aw.valid) && state===s_IDLE
  io.master1.aw.ready := io.slave.aw.ready  && state === s_IDLE
  io.master2.aw.ready := io.master1.aw.ready && !io.master1.aw.valid

  //Write Data
  io.slave.w.bits := Mux(state===s_CPU1_WRITE,io.master1.w.bits,io.master2.w.bits)
  io.slave.w.valid := MuxCase(false.B,Seq(
    (state === s_CPU1_WRITE )-> io.master1.w.valid,
    (state === s_CPU2_WRITE )-> io.master2.w.valid
  ))
  io.master1.w.ready := io.slave.w.ready && state===s_CPU1_WRITE
  io.master2.w.ready := io.slave.w.ready && state===s_CPU2_WRITE

  //Write ACK
  io.master1.b.bits := io.slave.b.bits
  io.master2.b.bits := io.slave.b.bits
  io.master1.b.valid := io.slave.b.valid && state === s_CPU1_ACK
  io.master2.b.valid := io.slave.b.valid && state === s_CPU2_ACK
  io.slave.b.ready := MuxCase(false.B,Seq(
    (state === s_CPU1_ACK) -> io.master1.b.ready,
    (state === s_CPU1_ACK) -> io.master2.b.ready,
  ))

  // Read Address
  io.slave.ar.bits := NastiReadAddressChannel(
    Mux(io.master1.ar.valid, io.master1.ar.bits.id, io.master2.ar.bits.id),
    Mux(io.master1.ar.valid, io.master1.ar.bits.addr, io.master2.ar.bits.addr),
    Mux(io.master1.ar.valid, io.master1.ar.bits.size, io.master2.ar.bits.size),
    Mux(io.master1.ar.valid, io.master1.ar.bits.len, io.master2.ar.bits.len))
  io.slave.ar.valid := (io.master2.ar.valid || io.master1.ar.valid) &&
    !io.slave.aw.valid && state === s_IDLE
  io.master1.ar.ready := io.slave.ar.ready && !io.slave.aw.valid && state === s_IDLE
  io.master2.ar.ready := io.master1.ar.ready && !io.master1.ar.valid

  // Read Data
  io.master1.r.bits := io.slave.r.bits
  io.master2.r.bits := io.slave.r.bits
  io.master1.r.valid := io.slave.r.valid && state === s_CPU1_READ
  io.master2.r.valid := io.slave.r.valid && state === s_CPU2_READ
  io.slave.r.ready := io.master2.r.ready && state === s_CPU2_READ ||
    io.master1.r.ready && state === s_CPU1_READ


  switch(state){
    is(s_IDLE){
      when(io.master1.aw.fire()){
        state := s_CPU1_WRITE
      }.elsewhen(io.master2.aw.fire()){
        state:=s_CPU2_WRITE
      }.elsewhen(io.master1.ar.fire()){
        state:=s_CPU1_READ
      }.elsewhen(io.master2.ar.fire()){
        state:=s_CPU2_READ
      }
    }
    is(s_CPU1_WRITE){
      when(io.master1.w.fire() && io.master1.w.bits.last){
        state := s_CPU1_ACK
      }
    }
      is(s_CPU2_WRITE){
      when(io.master2.w.fire() && io.master2.w.bits.last){
        state := s_CPU2_ACK
      }
    }
    is(s_CPU1_ACK){
      when(io.slave.b.fire()){
        state := s_IDLE
      }
    }
    is(s_CPU2_ACK){
      when(io.slave.b.fire()){
        state := s_IDLE
      }
    }
    is(s_CPU1_READ){
      when(io.slave.r.fire()&&io.slave.r.bits.last){
        state := s_IDLE
      }
    }
    is(s_CPU2_READ){
      when(io.slave.r.fire()&&io.slave.r.bits.last){
        state := s_IDLE
      }
    }
  }
}
