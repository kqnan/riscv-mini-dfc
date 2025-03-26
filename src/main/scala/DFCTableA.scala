package mini

import chisel3._
import chisel3.util._
import junctions._
import freechips.rocketchip.config.{Field, Parameters}

// --------------------------Table A------------------------------//

class DFC_TableA_IO(implicit p: Parameters) extends DFCBundle()(p){
  val cpu_w = Flipped(new DFC_TableA_Write_Channel)
  val cpu_l = Flipped(new DFC_TableA_Listen_Channel)
  val tableD = new DFC_TableAD_Channel

  // interrupt
  val cpu_expt = new DFC_Expt_Channel  // 发出中断，但不会接受中断反馈，即不等待中断程序完成
  val dfc_full = new DFC_Full_Channel
}

class A_infoMeta(implicit p: Parameters) extends DFCBundle()(p){
  val pid = UInt(pidlen.W)
}

class DFC_TableA(implicit val p: Parameters) extends Module with DFCParams{
  val io = IO(new DFC_TableA_IO)
  
  // TableA
  val anum = 1 << alen
  val counter = Reg(Vec(anum, UInt(cbits.W)))
  val infoMetaMem = SyncReadMem(anum, new A_infoMeta)
  val tableA_valid = RegInit(0.U(anum.W))

  val s_IDLE :: s_FOUND :: s_WRITE :: s_TOFULL :: s_FULL_A :: s_FULL_D :: s_WAIT :: s_DOWN_C :: s_INIT :: Nil = Enum(9)
  val state = RegInit(s_IDLE)
  val laststate = RegNext(state)
  val is_idle = state === s_IDLE

  // reg
  val laddr_reg = RegInit(0.U(xlen.W))
  val pid_reg = RegInit(0.U(pidlen.W))
  val pid_act = RegInit(0.U(pidlen.W))
  val table_wId = RegInit(1.U(alen.W))
  val full_a = RegInit(false.B)
  val full_a_last = RegNext(full_a)
  val full_d = RegInit(false.B)
  val full_d_last = RegNext(full_d)

  val wEn = is_idle && io.cpu_w.data.valid

  // io
  io.cpu_w.data.ready := is_idle
  io.cpu_l.data.ready := is_idle || state === s_FULL_A || state === s_FULL_D

  io.tableD.wdata.valid := false.B
  io.tableD.wdata.bits.listenAddr := laddr_reg
  io.tableD.wdata.bits.tableAId := table_wId
  io.tableD.wresp.ready := false.B

  io.tableD.ldata.valid := false.B
  io.tableD.ldata.bits.listenAddr := io.cpu_l.data.bits.listenAddr
  io.tableD.lresp.ready := false.B

  io.cpu_expt.data.valid := false.B
  io.cpu_expt.data.bits.pid := Mux(laststate === s_INIT, pid_reg, pid_act)   // avoid pid_act invalid

  io.dfc_full.data.valid := ((full_a_last || full_d_last) && (!full_a && !full_d)) ||
                            ((!full_a_last && !full_d_last) && (full_a || full_d))
  io.dfc_full.data.bits.full_sig := (!full_a_last && !full_d_last) && (full_a || full_d)

  // 
  when(wEn){
    laddr_reg := io.cpu_w.data.bits.listenAddr
    pid_reg := io.cpu_w.data.bits.wPid
  }

  // empty tableA id
  var empty_addr = 0.U
  for (i <- 1 to (anum-1)){
    empty_addr = Mux(tableA_valid(i), empty_addr, i.U)
  }
  // match pid 理论上不该存在匹配上却失效的项
  var match_addr = 0.U
  for (i <- 1 to (anum-1)){
    match_addr = Mux(io.cpu_w.data.bits.wPid === infoMetaMem.read(i.asUInt).pid , i.U, match_addr)
  }
  val foundAddr = RegEnable(match_addr, wEn)
  val notFound  = foundAddr === 0.U || !tableA_valid(foundAddr)
  val notEmpty  = empty_addr === 0.U || empty_addr(alen-1, log2Ceil(fifolen)) === 0.U

  // 在 s_FOUND 状态，查找应该已经完成
  when(state === s_FOUND){
    table_wId := Mux(notFound, empty_addr, foundAddr)
  }
  when(io.tableD.lresp.fire()){
    table_wId := io.tableD.lresp.bits.tableAId
  }

  // write
  val winfoMeta = Wire(new A_infoMeta)
  winfoMeta.pid := pid_reg
  val winfoInit = Wire(new A_infoMeta)
  winfoInit.pid := 0.U
  when(state === s_WRITE){
    when(notFound){
      infoMetaMem.write(table_wId, winfoMeta)
      counter(table_wId) := 1.U
      tableA_valid := tableA_valid.bitSet(table_wId, true.B)

      printf("[WTA](id: %d).infoMeta.pid = %d\n", table_wId, pid_reg)
      printf("[WC](id: %d).counter = %d\n", table_wId, 1.U)
    }.otherwise{
      counter(table_wId) := counter(table_wId) + 1.U

      printf("[WTA_EXIST](id: %d).infoMeta.pid = %d\n", table_wId, pid_reg)
      printf("[WC](id: %d).counter = %d\n", table_wId, counter(table_wId))
    }
  }
  // count down
  when(state === s_DOWN_C && counter(table_wId) =/= 1.U){
    counter(table_wId) := counter(table_wId) - 1.U
    printf("[WC_DOWN](id: %d).counter = %d\n", table_wId, counter(table_wId))
  }

  // 只在s_INIT的第一个时钟有效，后面由于INIT导致读出的值无效了
  pid_act := infoMetaMem.read(io.tableD.lresp.bits.tableAId, io.tableD.lresp.fire()).pid
  when(state === s_INIT && laststate =/= s_INIT){
    pid_reg := pid_act
  }
  // item init
  when(state === s_INIT && laststate =/= s_INIT){  // Avoid duplication
    infoMetaMem.write(table_wId, winfoInit)
    tableA_valid := tableA_valid.bitSet(table_wId, false.B)

    printf("[LTA](id: %d).infoMeta.pid Init!\n", table_wId)
  }

  // TableA FSM
  switch(state) {
    is(s_IDLE) {  // 0
      when(io.cpu_w.data.valid){
        state := s_FOUND
      }.elsewhen(io.cpu_l.data.valid){
        io.tableD.ldata.valid := true.B
        when(io.tableD.ldata.fire()){    // tableD must be ready
          state := s_WAIT
        }
      }
    }
    is(s_FOUND) {  // 1
      state := s_WRITE
    }
    is(s_WRITE){  // 2
      io.tableD.wdata.valid := true.B
      when(io.tableD.wdata.fire()){
        state := s_TOFULL
      }
    }
    is(s_TOFULL){  // 3
      io.tableD.wresp.ready := true.B
      when(io.tableD.wresp.fire()){
        full_d := io.tableD.wresp.bits
        when(notEmpty){
          full_a := true.B
          state  := s_FULL_A
        }.elsewhen(io.tableD.wresp.bits){
          state  := s_FULL_D
        }.otherwise{
          state  := s_IDLE
        }
      }
    }

    is(s_WAIT){  // 6
      io.tableD.lresp.ready := true.B
      when(io.tableD.lresp.fire()){
        when(io.tableD.lresp.bits.finish){
          when(full_a){
            state := s_FULL_A
          }.elsewhen(full_d){
            state := s_FULL_D
          }.otherwise{
            state := s_IDLE
          }
        }.otherwise{
          state := s_DOWN_C
        }
      }
    }
    is(s_DOWN_C){ // 7
      full_d := false.B
      when(counter(table_wId) === 1.U){
        full_a := false.B
        state := s_INIT
      }.otherwise{
        state := s_WAIT
      }
    }
    is(s_INIT){  // 8
      io.cpu_expt.data.valid := true.B
      when(io.cpu_expt.data.fire()){
        state := s_WAIT
      }
    }
    is(s_FULL_A){ // 4
      when(io.cpu_l.data.valid){
        io.tableD.ldata.valid := true.B
        when(io.tableD.ldata.fire()){    // tableD must be ready
          state := s_WAIT
        }
      }
    }
    is(s_FULL_D){ // 5
      when(io.cpu_l.data.valid){
        io.tableD.ldata.valid := true.B
        when(io.tableD.ldata.fire()){    // tableD must be ready
          state := s_WAIT
        }
      }
    }
  }
}


