// See LICENSE for license details.

package mini

import chisel3._
import freechips.rocketchip.config.Parameters


//寄存器文件,五个输入两个输出
//读取时:两个输入地址一个输出数据
//写入时:一个输入地址一个输出数据
//一位控制信号
class RegFileIO(implicit p: Parameters)  extends CoreBundle()(p) {
  val raddr1 = Input(UInt(5.W))     //要读取寄存器1、2的地址
  val raddr2 = Input(UInt(5.W))
  val rdata1 = Output(UInt(xlen.W)) //寄存器1、2的数据
  val rdata2 = Output(UInt(xlen.W))
  val wen    = Input(Bool())        //写信号
  val waddr  = Input(UInt(5.W))     //写地址
  val wdata  = Input(UInt(xlen.W))  //写数据
}

class RegFile(implicit val p: Parameters) extends Module with CoreParams {
  val io = IO(new RegFileIO)
  val regs = Mem(32, UInt(xlen.W))                     //数量为32长度为xlen的寄存器阵列
  //使用缩减或判断寄存器号是否为0
  io.rdata1 := Mux(io.raddr1.orR, regs(io.raddr1), 0.U) //若寄存器号不为0则读取数据,反之为0
  io.rdata2 := Mux(io.raddr2.orR, regs(io.raddr2), 0.U)
  when(io.wen & io.waddr.orR) {   //若写入寄存器号不为0且写入信号为1则写入数据
    regs(io.waddr) := io.wdata
  }
}
