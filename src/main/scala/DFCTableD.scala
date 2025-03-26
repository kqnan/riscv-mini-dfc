package mini

import chisel3._
import chisel3.util._
import junctions._
import freechips.rocketchip.config.{Field, Parameters}

class D_addrMeta(implicit p: Parameters) extends DFCBundle()(p){
  val listenAddr = UInt(xlen.W)
}

class D_infoMeta(implicit p: Parameters) extends DFCBundle()(p){
  val tableAid = UInt(alen.W)
}

class DFC_TableD_IO(implicit p: Parameters) extends DFCBundle()(p) {
  val tableA = Flipped(new DFC_TableAD_Channel)
}

class DFC_TableD(implicit val p: Parameters) extends Module with DFCParams{
  val io = IO(new DFC_TableD_IO)

  // Table D
  val dnum = 1 << dlen
  val addrMetaMem = SyncReadMem(dnum, new D_addrMeta)  // 这个实现有待考究？？？
  val infoMetaMem = SyncReadMem(dnum, new D_infoMeta)
  val tableD_valid = RegInit(0.U(dnum.W))

  val s_IDLE :: s_WRITE :: s_WRESP :: s_MATCH :: s_LRESP :: s_DELETE :: Nil = Enum(6)
  val state = RegInit(s_IDLE)
  val laststate = RegNext(state)
  val is_idle = state === s_IDLE

  // reg
  val laddr_reg = RegInit(0.U(xlen.W))
  val tableAid_reg = RegInit(0.U(alen.W))
  val tableD_Id = RegInit(1.U(dlen.W))
  val match_Id = RegInit(1.U(dlen.W))
  val cDown_Id = RegInit(1.U(alen.W))          //  时间久了会消失！！！
  val cDown_Id_reg = RegInit(1.U(alen.W))      //  保存cDown_Id,慢一个时钟
  cDown_Id_reg := Mux(state===s_DELETE, Mux(laststate===s_LRESP, cDown_Id, cDown_Id_reg), cDown_Id_reg)

  // io
  io.tableA.wdata.ready := is_idle
  io.tableA.ldata.ready := is_idle

  io.tableA.wresp.valid := false.B
  io.tableA.wresp.bits  := false.B

  io.tableA.lresp.valid := false.B
  io.tableA.lresp.bits.finish := true.B         // true表示匹配结束，cDown_Id无效
  io.tableA.lresp.bits.tableAId := Mux(cDown_Id===0.U, cDown_Id_reg, cDown_Id)

  // found empty 
  var empty_addr = 0.U
  for (i <- 1 to (dnum-1)){
    empty_addr = Mux(tableD_valid(i), empty_addr, i.U)
  }
  val notEmpty  = empty_addr === 0.U || empty_addr(dlen-1, log2Ceil(fifolen)) === 0.U
  when(is_idle && io.tableA.wdata.valid){
    laddr_reg := io.tableA.wdata.bits.listenAddr
    tableAid_reg := io.tableA.wdata.bits.tableAId
    tableD_Id := empty_addr
  }
  when(is_idle && io.tableA.ldata.valid){
    laddr_reg := io.tableA.ldata.bits.listenAddr
  }

  // match
  var match_addr = 0.U
  for (i <- 1 to (dnum-1)){
    match_addr = Mux(laddr_reg === addrMetaMem.read(i.asUInt).listenAddr , i.U, match_addr)
  }
  when(state === s_MATCH){
    match_Id := match_addr
  }
  val notMatched = match_Id === 0.U || !tableD_valid(match_Id)
  cDown_Id := infoMetaMem.read(match_addr).tableAid
  
  // write or init
  val listenAddrMeta = Wire(new D_addrMeta)
  listenAddrMeta.listenAddr := laddr_reg
  val winfoMeta = Wire(new D_infoMeta)
  winfoMeta.tableAid := tableAid_reg
  val listenInit = Wire(new D_addrMeta)
  listenInit.listenAddr := 0.U
  val tableAidInit = Wire(new D_infoMeta)
  tableAidInit.tableAid := 0.U
  when(state === s_WRITE){
    addrMetaMem.write(tableD_Id, listenAddrMeta)
    infoMetaMem.write(tableD_Id, winfoMeta)
    tableD_valid := tableD_valid.bitSet(tableD_Id, true.B)
    printf("[WTD](id: %d).addrMeta.listenAddr = %x\n", tableD_Id, laddr_reg)
    printf("[WTD](id: %d).infoMeta.tableAid = %d\n", tableD_Id, tableAid_reg)
  }
  when(state === s_DELETE && laststate =/= s_DELETE){   // Avoid duplication
    addrMetaMem.write(match_Id, listenInit)
    infoMetaMem.write(match_Id, tableAidInit)
    tableD_valid := tableD_valid.bitSet(match_Id, false.B)
    printf("[LTD](id: %d).addrMeta.listenAddr init\n", match_Id)
    printf("[LTD](id: %d).infoMeta.tableAid init\n", match_Id)
  }

  // TableD FSM
  switch(state) {
    is(s_IDLE) { // 0
      when(io.tableA.wdata.valid){
        state := s_WRITE
      }.elsewhen(io.tableA.ldata.valid){
        state := s_MATCH
      }
    }
    is(s_WRITE){ // 1
      state := s_WRESP
    }
    is(s_WRESP){ // 2
      io.tableA.wresp.valid := true.B
      when(notEmpty){
        io.tableA.wresp.bits := true.B
      }
      when(io.tableA.wresp.fire()){
        state := s_IDLE
      }
    }

    is(s_MATCH){  // 3
      state := s_LRESP
    }
    is(s_LRESP){  // 4
      when(notMatched){
        io.tableA.lresp.valid := true.B
        io.tableA.lresp.bits.finish := true.B
        when(io.tableA.lresp.fire()){
          state := s_IDLE
        }
      }.otherwise{
        state := s_DELETE
      }
    }
    is(s_DELETE){  // 5                         // 此状态cDownid才能读出值来
      io.tableA.lresp.valid := true.B
      io.tableA.lresp.bits.finish := false.B
      when(io.tableA.lresp.fire()){
        state := s_MATCH
      }
    }
  }
}

