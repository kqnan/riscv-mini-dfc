package mini

import chisel3.{Mux, _}
import chisel3.util._
import chisel3.util.experimental._
import firrtl.FirrtlProtos.Firrtl.Expression
import freechips.rocketchip.config.Parameters

class MMUIO(implicit val p: Parameters) extends Bundle {
  val cpu = new CacheIO
  val cache = Flipped(new CacheIO)
}

class MMU(mmuName: String, implicit val p: Parameters) extends Module with CoreParams {
  val io = IO(new MMUIO())
  //const val
  val PAGESIZE = 4096.U                           // 页大小
  val PTESIZE = 4.U                               // 页表项大小？

  //visit satp reg through boring                 // 以下是链接到CSR中的寄存器
  val satpReg = WireInit(0.U(xlen.W))             // 基址寄存器
  val csrWdata = WireInit(0.U(xlen.W))            // CSR io.in；要么是立即数或者rs1
  val vmSwitch = WireInit(false.B)                // 判断是否采用虚拟地址
  val stall = WireInit(false.B)                   // core 流水停止
  val clearPageFault = WireInit(false.B)          // 清除页错误的标志
  BoringUtils.addSink(satpReg, "satp")
  BoringUtils.addSink(vmSwitch, "vmSwitch")
  BoringUtils.addSink(csrWdata, "csrWdata")
  BoringUtils.addSink(stall,"stall")
  BoringUtils.addSink(clearPageFault,"clearPageFault")

  val satp = Mux(vmSwitch,csrWdata,satpReg)       // 基址？？？

  //data read from cache
//  val rdata = RegEnable(io.cache.resp.bits.data, io.cache.resp.valid)
  val rdata = RegInit(0.U(xlen.W))

  //val in satp Register (Sv32)
  val MODE = satp(31)                            // 决定是否使用虚存、MODE=0即机器，MODE=1即用户-虚存
  val ASID = satp(30, 22)
  val PPN = satp(21, 0)

  //val in virtual address
  val cpu_addr   = RegEnable(io.cpu.req.bits.addr,io.cpu.req.valid)
  val VPN1       = cpu_addr(31, 22)              // PDX 10bits 页目录索引
  val VPN0       = cpu_addr(21, 12)              // PTX 10bits 页表索引
  val pageOffest = cpu_addr(11, 0)               // 12bits     页内偏移

  //val in Sv32 page table entry
  val PPN1 = rdata(31, 20)
  val PPN0 = rdata(19, 10)
  //dirty bit
  val D = rdata(7)
  //access bit
  val A = rdata(6)
  //global mapping
  val G = rdata(5)
  //user mode
  val U = rdata(4)
  //permission bits
  val X = rdata(3)
  val W = rdata(2)
  val R = rdata(1)
  //valid bit
  val V = rdata(0)

  //PTE 页表项
  val LevelOnePTE = Reg(UInt(xlen.W))
  val LevelTwoPTE = Reg(UInt(xlen.W))
  val LevelOnePTEAddress = PPN * PAGESIZE + VPN1 * PTESIZE               // PPN<<12 + VPN1<<2  PPN22位岂不溢出？
  val LevelTwoPTEAddress = Cat(PPN1, PPN0) * PAGESIZE + VPN0 * PTESIZE   // 同样疑问？不会溢出吗？

  val (s_IDLE :: s_START:: s_getLevelOnePTE :: s_checkLevelOnePTE:: s_getLevelTwoPTE  :: s_checkLevelTwoPTE ::
    s_writeData  :: s_readData :: s_exception :: Nil) = Enum(9)
  val state = RegInit(s_IDLE)

  //cache req
  val cacheReqAddr = RegInit(0.U(xlen.W))
  val cacheReqData  = RegInit(0.U(xlen.W))
  val cacheReqMask =  RegInit(0.U((xlen/8).W))
  val cacheReqValid = RegInit(false.B)

  io.cache.req.bits.addr := Mux(MODE === 0.U ,io.cpu.req.bits.addr,cacheReqAddr)
  io.cache.req.bits.data := Mux(MODE === 0.U ,io.cpu.req.bits.data,cacheReqData)
  io.cache.req.bits.mask := Mux(MODE === 0.U ,io.cpu.req.bits.mask,
                                Mux(state === s_checkLevelTwoPTE && !io.cpu.abort, io.cpu.req.bits.mask, 0.U))
  io.cache.req.valid := Mux(MODE === 0.U ,io.cpu.req.valid,false.B)
  io.cache.abort := io.cpu.abort

//  when(io.cpu.req.valid){printf("cpu req addr : %x\n",io.cpu.req.bits.data)}
//  when(io.cpu.resp.valid){printf("cpu resp data : %x\n",io.cpu.resp.bits.data)}

  //2 bits page fault signal
  // InstructionPageFault = 0x1.U
  // LoadPageFault        = 0x2.U
  // StorePageFault       = 0x3.U
  val pageFault = RegInit(0.U(2.W))
  val isInstruction = (mmuName == "immu").asBool()
  val isLoad = (mmuName == "dmmu").asBool() && io.cpu.req.bits.mask.xorR()
  val isStore = (mmuName == "dmmu").asBool() && !io.cpu.req.bits.mask.xorR()
  //boring
  BoringUtils.addSource(pageFault, mmuName)

  //physical address
  val physicalAddress = Cat(PPN1, Cat(PPN0, pageOffest))

  //cpu resp
  val cpuRespData = RegInit(0.U(xlen.W))
  io.cpu.resp.bits.data := Mux(MODE === 0.U,io.cache.resp.bits.data,cpuRespData)
  io.cpu.resp.valid := Mux(MODE === 0.U,io.cache.resp.valid,state === s_IDLE)

  //FSM
  switch(state) {
    is(s_IDLE){ //0
      when(clearPageFault){pageFault := 0.U}                           // 拉起异常后，需要恢复
      when(io.cpu.req.valid && !io.cpu.abort && MODE === 1.U){
//        printf(mmuName + " req address : %x \n",io.cpu.req.bits.addr)
        cacheReqAddr := io.cpu.req.bits.addr
        cacheReqData := io.cpu.req.bits.data
        cacheReqMask := io.cpu.req.bits.mask
        state := s_START
      }
    }
    is(s_START) { //1
        //init signal
      io.cache.req.bits.addr := LevelOnePTEAddress
      io.cache.req.valid := true.B
      state := s_getLevelOnePTE
    }
    is(s_getLevelOnePTE){ //2
      when(io.cache.resp.valid){
        rdata := io.cache.resp.bits.data
        state := s_checkLevelOnePTE
      }
    }
    is(s_checkLevelOnePTE) { //3
      when(io.cpu.abort) {
        state := s_IDLE
      }
        .elsewhen(V.asBool()) {
          io.cache.req.bits.addr := LevelTwoPTEAddress
          io.cache.req.valid := true.B
          state := s_getLevelTwoPTE
        }.otherwise {
        //exception
  //       printf(mmuName + " exception addr : %x , pageTable : %x \n",cpu_addr,rdata)
        pageFault := Mux(isInstruction, 0x1.U,
                        Mux(isLoad, 0x2.U,
                        Mux(isStore, 0x3.U, 0.U)))
        state := s_IDLE
        }
    }
    is(s_getLevelTwoPTE){   //4
      when(io.cache.resp.valid){
        rdata := io.cache.resp.bits.data
        state := s_checkLevelTwoPTE
      }
    }
    is(s_checkLevelTwoPTE) {  //5
      when(io.cpu.abort) {
        state := s_IDLE
      }
        .elsewhen(V.asBool()) {
          //cahce req signal
  //          printf(mmuName + " physicalAddress = %x\n",physicalAddress)
          io.cache.req.bits.addr := physicalAddress
          io.cache.req.bits.data := cacheReqData
          io.cache.req.bits.mask := cacheReqMask
          io.cache.req.valid := true.B
          //change state
          state := Mux(io.cpu.req.bits.mask.orR, s_writeData, s_readData)
        }
        .otherwise {
          //exception
  //          printf(mmuName + " exception addr : %x \n pageTable : %x \n",cpu_addr,rdata)
          pageFault := Mux(isInstruction, 0x1.U,
                          Mux(isLoad, 0x2.U,
                          Mux(isStore, 0x3.U, 0.U)))
          state := s_IDLE
      }
    }
    is(s_writeData) {
      when(io.cpu.abort) {
        state := s_IDLE
      }
        .otherwise {
          when(io.cache.resp.valid) {
            state := s_IDLE
          }
        }
    }
    is(s_readData) {
      when(io.cpu.abort) {
        state := s_IDLE
      }
        .otherwise {
          when(io.cache.resp.valid) {
            cpuRespData := io.cache.resp.bits.data
//            printf("MMU resp data = %x\n",io.cache.resp.bits.data)
            state := s_IDLE
          }
        }
    }
  }
}
