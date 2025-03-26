// See LICENSE for license details.

package mini

import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import freechips.rocketchip.config.Parameters

object CSR {
  val N = 0.U(3.W)
  val W = 1.U(3.W)
  val S = 2.U(3.W)
  val C = 3.U(3.W)
  val P = 4.U(3.W)

  // Supports machine & user modes
  val PRV_U = 0x0.U(2.W)
  val PRV_M = 0x3.U(2.W)

  // User-level CSR addrs
  val cycle    = 0xc00.U(12.W)
  val time     = 0xc01.U(12.W)
  val instret  = 0xc02.U(12.W)
  val cycleh   = 0xc80.U(12.W)
  val timeh    = 0xc81.U(12.W)
  val instreth = 0xc82.U(12.W)

  // Supervisor-level CSR addrs
  val cyclew    = 0x900.U(12.W)
  val timew     = 0x901.U(12.W)
  val instretw  = 0x902.U(12.W)
  val cyclehw   = 0x980.U(12.W)
  val timehw    = 0x981.U(12.W)
  val instrethw = 0x982.U(12.W)
  //Supervisor Protection and Translation
  val satp      = 0x180.U(12.W)

  // Machine-level CSR addrs
  // Machine Information Registers
  val mcpuid   = 0xf00.U(12.W)
  val mimpid   = 0xf01.U(12.W)
  val mhartid  = 0xf10.U(12.W)
  // Machine Trap Setup
  val mstatus  = 0x300.U(12.W)
  val mtvec    = 0x301.U(12.W)
  val mtdeleg  = 0x302.U(12.W)
  val mie      = 0x304.U(12.W)
  val mtimecmp = 0x321.U(12.W)
  // Machine Timers and Counters
  val mtime    = 0x701.U(12.W)
  val mtimeh   = 0x741.U(12.W)
  // Machine Trap Handling
  val mscratch = 0x340.U(12.W)
  val mepc     = 0x341.U(12.W)
  val mcause   = 0x342.U(12.W)
  val mbadaddr = 0x343.U(12.W)
  val mip      = 0x344.U(12.W)
  // Machine HITF
  val mtohost   = 0x780.U(12.W)
  val mfromhost = 0x781.U(12.W)

  // DFC
  val dfcpid       = 0x800.U(12.W)
  // val dfctablefull = 0x801.U(12.W)  // 不可读写，无需标识

  val regs = List(
    cycle, time, instret, cycleh, timeh, instreth,
    cyclew, timew, instretw, cyclehw, timehw, instrethw,
    mcpuid, mimpid, mhartid, mtvec, mtdeleg, mie,
    mtimecmp, mtime, mtimeh, mscratch, mepc, mcause, mbadaddr, mip,
    mtohost, mfromhost, mstatus, satp, dfcpid)
}

object Cause {
  val InstAddrMisaligned  = 0x0.U
  val IllegalInst         = 0x2.U
  val Breakpoint          = 0x3.U
  val LoadAddrMisaligned  = 0x4.U
  val StoreAddrMisaligned = 0x6.U
  val Ecall               = 0x8.U
  //page fault
  val InstructionPageFault= 0xC.U
  val LoadPageFault       = 0xD.U
  val StorePageFault      = 0xF.U
  //timer interrupt
  val timerInterrupt      = "h80000001".U
  //uart rx interrupt
  val uartRxInterrupt     = 0x10.U

  // DFC
  val DfCAct              = 0x12.U
  val DFCFull             = 0x14.U
}

class CSRIO(implicit p: Parameters)  extends DFCBundle()(p) {
  val stall = Input(Bool())
  val cmd   = Input(UInt(3.W))
  val in    = Input(UInt(xlen.W))
  val out   = Output(UInt(xlen.W))
  // Excpetion
  val pc       = Input(UInt(xlen.W))
  val addr     = Input(UInt(xlen.W))
  val inst     = Input(UInt(xlen.W))
  val illegal  = Input(Bool())
  val st_type  = Input(UInt(2.W))
  val ld_type  = Input(UInt(3.W))
  val pc_check = Input(Bool())
  val expt     = Output(Bool())
  val evec     = Output(UInt(xlen.W))
  val epc      = Output(UInt(xlen.W))
  //
  val vmSwitch = Output(Bool())
  val vmSwitchPC = Output(UInt(xlen.W))
  // HTIF
  val host = new HostIO

  // DFC
  val dfc_cmd    = Input(UInt(3.W))
  val dfc_pid    = Input(UInt(pidlen.W))
  val table_f    = Flipped(new DFC_Full_Channel)
  val dfc_full   = Output(Bool())
}

class CSR(implicit val p: Parameters) extends Module with CoreParams {
  val io = IO(new CSRIO)

  val csr_addr = io.inst(31, 20)
  val rs1_addr = io.inst(19, 15)

  // user counters
  val time     = RegInit(0.U(xlen.W))
  val counter  = RegInit(0.U(xlen.W))
  val timeh    = RegInit(0.U(xlen.W))
  val cycle    = RegInit(0.U(xlen.W))
  val cycleh   = RegInit(0.U(xlen.W))
  val instret  = RegInit(0.U(xlen.W))
  val instreth = RegInit(0.U(xlen.W))

  val mcpuid  = Cat(0.U(2.W) /* RV32I */, 0.U((xlen-28).W),
                    (1 << ('I' - 'A') /* Base ISA */|
                     1 << ('U' - 'A') /* User Mode */).U(26.W))
  val mimpid  = 0.U(xlen.W) // not implemented
  val mhartid = 0.U(xlen.W) // only one hart

  // interrupt enable stack
  val PRV  = RegInit(CSR.PRV_M)
  val PRV1 = RegInit(CSR.PRV_M)
  val PRV2 = 0.U(2.W)
  val PRV3 = 0.U(2.W)
  val IE  = RegInit(false.B)
  val IE1 = RegInit(false.B)
  val IE2 = false.B
  val IE3 = false.B
  // virtualization management field
  val VM = 0.U(5.W)
  // memory privilege
  val MPRV = false.B
  // extention context status
  val XS = 0.U(2.W)
  val FS = 0.U(2.W)
  val SD = 0.U(1.W)
  val mstatus = Cat(SD, 0.U((xlen-23).W), VM, MPRV, XS, FS, PRV3, IE3, PRV2, IE2, PRV1, IE1, PRV, IE)
  val mtvec   = RegInit(Const.PC_EVEC.U(xlen.W))
  val mtdeleg = 0x0.U(xlen.W)

  // interrupt registers
  val MTIP = RegInit(false.B)
  val HTIP = false.B
  val STIP = false.B
  val MTIE = RegInit(false.B)
  val HTIE = false.B
  val STIE = false.B
  val MSIP = RegInit(false.B)
  val HSIP = false.B
  val SSIP = false.B
  val MSIE = RegInit(false.B)
  val HSIE = false.B
  val SSIE = false.B
  val mip = Cat(0.U((xlen-8).W), MTIP, HTIP, STIP, false.B, MSIP, HSIP, SSIP, false.B)
  val mie = Cat(0.U((xlen-8).W), MTIE, HTIE, STIE, false.B, MSIE, HSIE, SSIE, false.B)
  val timerInterrupt = RegInit(false.B)
  val uartInterruptPending = RegInit(false.B)

  val mtimecmp = Reg(UInt(xlen.W))

  val mscratch = Reg(UInt(xlen.W))

  val mepc = Reg(UInt(xlen.W))
  val mcause = Reg(UInt(xlen.W))
  val mbadaddr = Reg(UInt(xlen.W))

  val mtohost = RegInit(0.U(xlen.W))
  val mfromhost = Reg(UInt(xlen.W))
  // io.host.tohost := mtohost
  io.host.tohost := mcause                   // 
  when(io.host.fromhost.valid) {
    mfromhost := io.host.fromhost.bits
  }

  //Supervisor CSR Reg
  val satp = RegInit("x00000000".U(xlen.W))  //Base Address Register

  //pageFault Signal
  val immuPageFault = WireInit(0.U(2.W))
  val dmmuPageFault = WireInit(0.U(2.W))
  val pageFault = Mux(immuPageFault =/= 0.U, immuPageFault,dmmuPageFault)
  val clearPageFault = WireInit(false.B)
  val uartRxValid = WireInit(false.B)

  //Boring
  BoringUtils.addSource(satp,"satp")
  BoringUtils.addSource(io.stall,"stall")
  BoringUtils.addSource(io.vmSwitch,"vmSwitch")
  BoringUtils.addSource(io.in,"csrWdata")
  BoringUtils.addSource(clearPageFault,"clearPageFault")
  BoringUtils.addSink(immuPageFault,"immu")
  BoringUtils.addSink(dmmuPageFault,"dmmu")
  BoringUtils.addSink(uartRxValid,"uartRxValid")

  when(uartRxValid){uartInterruptPending := true.B}

  // DFC
  val dfc_pid = RegInit(0.U(xlen.W))
  val dfc_full_reg = RegInit(false.B)
  io.dfc_full := dfc_full_reg

  val csrFile = Seq(
    BitPat(CSR.cycle)     -> cycle,
    BitPat(CSR.time)      -> time,
    BitPat(CSR.instret)   -> instret,
    BitPat(CSR.cycleh)    -> cycleh,
    BitPat(CSR.timeh)     -> timeh,
    BitPat(CSR.instreth)  -> instreth,
    BitPat(CSR.cyclew)    -> cycle,
    BitPat(CSR.timew)     -> time,
    BitPat(CSR.instretw)  -> instret,
    BitPat(CSR.cyclehw)   -> cycleh,
    BitPat(CSR.timehw)    -> timeh,
    BitPat(CSR.instrethw) -> instreth,
    BitPat(CSR.mcpuid)    -> mcpuid,
    BitPat(CSR.mimpid)    -> mimpid,
    BitPat(CSR.mhartid)   -> mhartid,
    BitPat(CSR.mtvec)     -> mtvec,
    BitPat(CSR.mtdeleg)   -> mtdeleg,
    BitPat(CSR.mie)       -> mie,
    BitPat(CSR.mtimecmp)  -> mtimecmp,
    BitPat(CSR.mtime)     -> time,
    BitPat(CSR.mtimeh)    -> timeh,
    BitPat(CSR.mscratch)  -> mscratch,
    BitPat(CSR.mepc)      -> mepc,
    BitPat(CSR.mcause)    -> mcause,
    BitPat(CSR.mbadaddr)  -> mbadaddr,
    BitPat(CSR.mip)       -> mip,
    BitPat(CSR.mtohost)   -> mtohost,
    BitPat(CSR.mfromhost) -> mfromhost,
    BitPat(CSR.mstatus)   -> mstatus,
    BitPat(CSR.satp)      -> satp, 

    BitPat(CSR.dfcpid)    -> dfc_pid
  )

  io.out := Lookup(csr_addr, 0.U, csrFile).asUInt

  val privValid = csr_addr(9, 8) <= PRV
  val privInst  = io.cmd === CSR.P
  val isEcall   = privInst && !csr_addr(0) && !csr_addr(8)
  val isEbreak  = privInst &&  csr_addr(0) && !csr_addr(8)
  val isEret    = privInst && !csr_addr(0) &&  csr_addr(8)
  val csrValid  = csrFile map (_._1 === csr_addr) reduce (_ || _)
  val csrRO     = csr_addr(11, 10).andR || csr_addr === CSR.mtdeleg
  val wen       = io.cmd === CSR.W || io.cmd(1) && rs1_addr.orR
  val wdata     = MuxLookup(io.cmd, 0.U, Seq(
    CSR.W -> io.in,
    CSR.S -> (io.out | io.in),
    CSR.C -> (io.out & ~io.in)
  ))
  val iaddrInvalid = io.pc_check && io.addr(1)
  val laddrInvalid = MuxLookup(io.ld_type, false.B, Seq(
    Control.LD_LW -> io.addr(1, 0).orR, Control.LD_LH -> io.addr(0), Control.LD_LHU -> io.addr(0)))
  val saddrInvalid = MuxLookup(io.st_type, false.B, Seq(
    Control.ST_SW -> io.addr(1, 0).orR, Control.ST_SH -> io.addr(0)))
  //check page fault
  val isInstructionFault = pageFault === 1.U
  val isLoadFault        = pageFault === 2.U
  val isStoreFault       = pageFault === 3.U
  //vm
  val vmSwitch = csr_addr === CSR.satp && wen
  val vmSwitchPC = RegInit(0.U(xlen.W))
  io.vmSwitchPC := Mux(io.stall,vmSwitchPC,io.pc)
  io.vmSwitch := vmSwitch

  val isUartInterrupt = IE && uartInterruptPending && (io.inst =/= Instructions.NOP) && !pageFault
  val isTimerInterrupt = MTIP && MTIE && IE && (io.inst =/= Instructions.NOP) && !pageFault && !isUartInterrupt

  // DFC expt
  val isDFCAct  = io.dfc_pid.orR()
  val writeDFCInvalid = dfc_full_reg && io.dfc_cmd === Control.DFC_WT
  when(isDFCAct){
    dfc_pid := io.dfc_pid      // 此处是否会自动拓展到xlen???
  }
  when(io.table_f.data.valid){
    dfc_full_reg := io.table_f.data.bits.full_sig
  }
  val t_expt = io.illegal || iaddrInvalid || laddrInvalid || saddrInvalid ||
                io.cmd(1, 0).orR && (!csrValid || !privValid) || wen && csrRO ||
                (privInst && !privValid) || isEcall || isEbreak || isInstructionFault||
                isLoadFault || isStoreFault || isTimerInterrupt || isUartInterrupt
  io.expt :=  t_expt || isDFCAct || writeDFCInvalid

  io.evec := mtvec
  io.epc  := mepc

  // Counters
  counter := counter + 1.U
  when(counter(10,0).andR()){ time := time + 1.U}
  when(time.andR) { timeh := timeh + 1.U }
  when(time === mtimecmp){MTIP := true.B}

  cycle := cycle + 1.U
  when(cycle.andR) { cycleh := cycleh + 1.U }
  val isInstRet = io.inst =/= Instructions.NOP && (!io.expt || isEcall || isEbreak) && !io.stall
  when(isInstRet) { instret := instret + 1.U }
  when(isInstRet && instret.andR) { instreth := instreth + 1.U }

  when(!io.stall) {
    when(io.expt) {
      mepc   := io.pc >> 2 << 2
      // 异常处理中，优先DFC异常，然后返回当前指令（即便是个异常指令）
      // writeDFCInvalid 暂不优先处理
      mcause := Mux(isDFCAct,     Cause.DfCAct, 
                Mux(iaddrInvalid, Cause.InstAddrMisaligned,
                Mux(laddrInvalid, Cause.LoadAddrMisaligned,
                Mux(saddrInvalid, Cause.StoreAddrMisaligned,
                Mux(isEcall,      Cause.Ecall + PRV,
                Mux(isEbreak,     Cause.Breakpoint,
                  //page fault
                Mux(isInstructionFault,Cause.InstructionPageFault,
                Mux(isLoadFault,Cause.LoadPageFault,
                Mux(isStoreFault,Cause.StorePageFault,
                  //timer interrupt
                Mux(isTimerInterrupt,Cause.timerInterrupt,
                  //uart rx interrupt
                Mux(isUartInterrupt,Cause.uartRxInterrupt,
                
                Mux(writeDFCInvalid, Cause.DFCFull, Cause.IllegalInst))))))))))))
      PRV  := CSR.PRV_M
      IE   := false.B
      PRV1 := PRV
      IE1  := IE
      //add page fault
      when(iaddrInvalid || laddrInvalid || saddrInvalid || isInstructionFault || isLoadFault || isStoreFault) { mbadaddr := io.addr }
      when(isUartInterrupt){uartInterruptPending := false.B}
      when(isInstructionFault || isLoadFault || isStoreFault){ clearPageFault := true.B }
    }.elsewhen(isEret) {
      PRV  := PRV1
      IE   := IE1
      PRV1 := CSR.PRV_U
      IE1  := true.B
    }.elsewhen(wen) {
      when(csr_addr === CSR.mstatus) {
        PRV1 := wdata(5, 4)
        IE1  := wdata(3)
        PRV  := wdata(2, 1)
        IE   := wdata(0)
      }
      .elsewhen(csr_addr === CSR.mip) {
        MTIP := wdata(7)
        MSIP := wdata(3)
      }
      .elsewhen(csr_addr === CSR.mie) {
        MTIE := wdata(7)
        MSIE := wdata(3)
      }
      .elsewhen(csr_addr === CSR.mtime) { time := wdata }
      .elsewhen(csr_addr === CSR.mtimeh) { timeh := wdata }
      .elsewhen(csr_addr === CSR.mtimecmp) { mtimecmp := wdata ; MTIP := false.B }
      .elsewhen(csr_addr === CSR.mscratch) { mscratch := wdata }
      .elsewhen(csr_addr === CSR.mepc) { mepc := wdata >> 2.U << 2.U }
      // .elsewhen(csr_addr === CSR.mcause) { mcause := wdata & (BigInt(1) << (xlen-1) | 0xf).U }
      .elsewhen(csr_addr === CSR.mcause) { mcause := wdata}
      .elsewhen(csr_addr === CSR.mbadaddr) { mbadaddr := wdata }
      .elsewhen(csr_addr === CSR.mtohost) { mtohost := wdata }
      .elsewhen(csr_addr === CSR.mfromhost) { mfromhost := wdata }
      .elsewhen(csr_addr === CSR.cyclew) { cycle := wdata }
      .elsewhen(csr_addr === CSR.timew) { time := wdata }
      .elsewhen(csr_addr === CSR.instretw) { instret := wdata }
      .elsewhen(csr_addr === CSR.cyclehw) { cycleh := wdata }
      .elsewhen(csr_addr === CSR.timehw) { timeh := wdata }
      .elsewhen(csr_addr === CSR.instrethw) { instreth := wdata }
      .elsewhen(csr_addr === CSR.satp ){ satp := wdata ; vmSwitchPC := io.pc ;}
      .elsewhen(csr_addr === CSR.mtvec ){ mtvec := wdata}
    }
  }
}
