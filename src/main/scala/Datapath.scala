// See LICENSE for license details.

package mini

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters

object Const {
//  val PC_START = 0x200
  val PC_EVEC  = 0x100
}

class DatapathIO(implicit p: Parameters) extends CoreBundle()(p) {
  val host = new HostIO
  val icache = Flipped(new CacheIO)
  val dcache = Flipped(new CacheIO)
  val ctrl = Flipped(new ControlSignals)

  // DFC
  val table_w = new DFC_TableA_Write_Channel
  val table_l = new DFC_TableA_Listen_Channel
  val table_e = Flipped(new DFC_Expt_Channel)
  val table_f = Flipped(new DFC_Full_Channel)
}

class Datapath(coreNum : Int,pc_start: Long,implicit val p: Parameters) extends Module with CoreParams {
  val io      = IO(new DatapathIO)
  val csr     = Module(new CSR)
  val regFile = Module(new RegFile) 
  val alu     = p(BuildALU)(p)
  val immGen  = p(BuildImmGen)(p)
  val brCond  = p(BuildBrCond)(p)

  val PC_START = pc_start

  import Control._

  /***** Fetch / Execute Registers *****/
  val fe_inst = RegInit(Instructions.NOP)
  val fe_pc   = Reg(UInt())

  /***** Execute / Write Back Registers *****/
  val ew_inst = RegInit(Instructions.NOP) 
  val ew_pc   = Reg(UInt())
  val ew_alu  = Reg(UInt())
  val csr_in  = Reg(UInt())

  /****** Control signals *****/
  val st_type  = Reg(io.ctrl.st_type.cloneType)
  val ld_type  = Reg(io.ctrl.ld_type.cloneType)
  val wb_sel   = Reg(io.ctrl.wb_sel.cloneType)
  val wb_en    = Reg(Bool())
  val csr_cmd  = Reg(io.ctrl.csr_cmd.cloneType)
  val illegal  = Reg(Bool())
  val pc_check = Reg(Bool())

  // DFC
  val csr_dfc_cmd = Reg(UInt(3.W))

  /****** Fetch *****/
  val started = RegNext(reset.toBool)
  val stall = !io.icache.resp.valid || !io.dcache.resp.valid
  val pc   = RegInit(PC_START.U(xlen.W) - 4.U(xlen.W))
  //下一条指令的地址，根据不同情况设置
  val npc  = Mux(stall, pc, Mux(csr.io.expt, csr.io.evec,
             //mepc is written with the virtual address of the instruction that encountered the exception
             Mux(io.ctrl.pc_sel === PC_EPC,  csr.io.epc,  //ERET instruction
             Mux(io.ctrl.pc_sel === PC_ALU || brCond.io.taken, alu.io.sum >> 1.U << 1.U,
             Mux(io.ctrl.pc_sel === PC_0, pc,
             Mux(csr.io.vmSwitch,csr.io.vmSwitchPC + 4.U,pc + 4.U))
             ))))

  //debug
//  when(csr.io.expt){printf("expt : pc = %x , evec = %x ,stall = %x \n",csr.io.epc,csr.io.evec,stall)}
//  when(io.ctrl.pc_sel === PC_EPC){printf("PC_EPC pc = %x\n",pc)}

  val inst = Mux(started || io.ctrl.inst_kill || brCond.io.taken || csr.io.expt || csr.io.vmSwitch, Instructions.NOP, io.icache.resp.bits.data)
  pc                      := npc 
  io.icache.req.bits.addr := npc
  io.icache.req.bits.data := 0.U
  io.icache.req.bits.mask := 0.U
  io.icache.req.valid     := !stall
  io.icache.abort         := false.B
 
  // Pipelining
  when (!stall) {
    fe_pc   := pc
    fe_inst := inst
  }

  /****** Execute *****/
  // Decode
  io.ctrl.inst  := fe_inst

  // regFile read
  val rd_addr  = fe_inst(11, 7)
  val rs1_addr = fe_inst(19, 15)
  val rs2_addr = fe_inst(24, 20)
  regFile.io.raddr1 := rs1_addr
  regFile.io.raddr2 := rs2_addr

  // gen immdeates
  immGen.io.inst := fe_inst
  immGen.io.sel  := io.ctrl.imm_sel

  // bypass
  val wb_rd_addr = ew_inst(11, 7)
  val rs1hazard = wb_en && rs1_addr.orR && (rs1_addr === wb_rd_addr)
  val rs2hazard = wb_en && rs2_addr.orR && (rs2_addr === wb_rd_addr)
  val rs1 = Mux(wb_sel === WB_ALU && rs1hazard, ew_alu, regFile.io.rdata1) 
  val rs2 = Mux(wb_sel === WB_ALU && rs2hazard, ew_alu, regFile.io.rdata2)
 
  // ALU operations
  alu.io.A := Mux(io.ctrl.A_sel === A_RS1, rs1, fe_pc)
  alu.io.B := Mux(io.ctrl.B_sel === B_RS2, rs2, immGen.io.out)
  alu.io.alu_op := io.ctrl.alu_op

  // Branch condition calc
  brCond.io.rs1 := rs1 
  brCond.io.rs2 := rs2
  brCond.io.br_type := io.ctrl.br_type

  // D$ access
  val daddr   = Mux(stall, ew_alu, alu.io.sum) >> 2.U << 2.U
  val woffset = alu.io.sum(1) << 4.U | alu.io.sum(0) << 3.U
  io.dcache.req.valid     := !stall && (io.ctrl.st_type.orR || io.ctrl.ld_type.orR)
  io.dcache.req.bits.addr := daddr 
  io.dcache.req.bits.data := rs2 << woffset
  io.dcache.req.bits.mask := MuxLookup(Mux(stall, st_type, io.ctrl.st_type), 
              "b0000".U, Seq(
    ST_SW ->  "b1111".U,
    ST_SH -> ("b11".U << alu.io.sum(1,0)),
    ST_SB -> ("b1".U  << alu.io.sum(1,0))))
  
  // Pipelining
  // 注意异常信号恢复，否则一直异常
  when(reset.toBool || !stall && csr.io.expt || !stall && csr.io.vmSwitch) {
    st_type   := 0.U
    ld_type   := 0.U
    wb_en     := false.B
    csr_cmd   := 0.U
    illegal   := false.B
    pc_check  := false.B

    csr_dfc_cmd := 0.U
  }.elsewhen(!stall && !csr.io.expt && !csr.io.vmSwitch) {
    ew_pc     := fe_pc
    ew_inst   := fe_inst
    ew_alu    := alu.io.out
    csr_in    := Mux(io.ctrl.imm_sel === IMM_Z, immGen.io.out, rs1)
    st_type   := io.ctrl.st_type
    ld_type   := io.ctrl.ld_type
    wb_sel    := io.ctrl.wb_sel
    wb_en     := io.ctrl.wb_en
    csr_cmd   := io.ctrl.csr_cmd
    illegal   := io.ctrl.illegal
    pc_check  := io.ctrl.pc_sel === PC_ALU

    csr_dfc_cmd := io.ctrl.dfc_cmd
  }

  // Load
  val loffset = ew_alu(1) << 4.U | ew_alu(0) << 3.U
  val lshift  = io.dcache.resp.bits.data >> loffset
  val load    = MuxLookup(ld_type, io.dcache.resp.bits.data.zext, Seq(
    LD_LH  -> lshift(15, 0).asSInt, LD_LB  -> lshift(7, 0).asSInt,
    LD_LHU -> lshift(15, 0).zext,   LD_LBU -> lshift(7, 0).zext) )
    
  // CSR access
  csr.io.stall    := stall
  csr.io.in       := csr_in
  csr.io.cmd      := csr_cmd
  csr.io.inst     := ew_inst
  csr.io.pc       := ew_pc
  csr.io.addr     := ew_alu
  csr.io.illegal  := illegal
  csr.io.pc_check := pc_check
  csr.io.ld_type  := ld_type
  csr.io.st_type  := st_type
  io.host <> csr.io.host 
  // dfc expt
  csr.io.dfc_cmd    := csr_dfc_cmd
  io.table_e.data.ready := !stall
  csr.io.dfc_pid    := Mux(io.table_e.data.valid, io.table_e.data.bits.pid, 0.U)

  csr.io.table_f <> io.table_f

  // Regfile Write
  val regWrite = MuxLookup(wb_sel, ew_alu.zext, Seq(
    WB_MEM -> load,
    WB_PC4 -> (ew_pc + 4.U).zext,
    WB_CSR -> csr.io.out.zext) ).asUInt 

  regFile.io.wen   := wb_en && !stall && !csr.io.expt  && !csr.io.vmSwitch
  regFile.io.waddr := wb_rd_addr
  regFile.io.wdata := regWrite

  // Abort store when there's an excpetion
  io.dcache.abort := csr.io.expt || csr.io.vmSwitch

  // DFC write/listen
  io.table_w.data.valid := io.ctrl.dfc_cmd === DFC_WT && !csr.io.dfc_full && !stall
  io.table_w.data.bits.listenAddr := rs2
  io.table_w.data.bits.wPid := rs1
  io.table_l.data.valid := io.ctrl.dfc_cmd === DFC_LT && !stall
  io.table_l.data.bits.listenAddr := rs2

//  when (true.B) {
//    printf("coreNum: %d , PC: %x, INST: %x, REG[%d] <- %x\n",coreNum.U, ew_pc, ew_inst,
//      Mux(regFile.io.wen, wb_rd_addr, 0.U),
//      Mux(regFile.io.wen, regFile.io.wdata, 0.U))
//  }
}
