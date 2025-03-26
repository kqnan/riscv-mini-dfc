// See LICENSE for license details.

package mini

import chisel3._
import chisel3.util._
import freechips.rocketchip.config.Parameters

//单例对象,存储ALU_op
object ALU {
  val ALU_ADD    = 0.U(4.W)
  val ALU_SUB    = 1.U(4.W)
  val ALU_AND    = 2.U(4.W)
  val ALU_OR     = 3.U(4.W)
  val ALU_XOR    = 4.U(4.W)
  val ALU_SLT    = 5.U(4.W) //set less than
  val ALU_SLL    = 6.U(4.W) //shift left logical
  val ALU_SLTU   = 7.U(4.W) //set less than unsigned
  val ALU_SRL    = 8.U(4.W) //shift right logical
  val ALU_SRA    = 9.U(4.W) //shift right arithmetic
  val ALU_COPY_A = 10.U(4.W)//直送
  val ALU_COPY_B = 11.U(4.W)
  val ALU_XXX    = 15.U(4.W)
}

//ALU的IO,包含两个数据输入,一个控制输入和两个输出(？)
class ALUIo(implicit p: Parameters) extends CoreBundle()(p) {
  val A = Input(UInt(xlen.W))
  val B = Input(UInt(xlen.W))
  val alu_op = Input(UInt(4.W))
  val out = Output(UInt(xlen.W))  //输出结果
  val sum = Output(UInt(xlen.W))
}

import ALU._  //引入单例对象,引入后可访问单例对象中的公开成员

//抽象伴生对象，可访问单例对象中的私有成员
abstract class ALU(implicit val p: Parameters) extends Module with CoreParams {
  val io = IO(new ALUIo)
}

class ALUSimple(implicit p: Parameters) extends ALU()(p) {
  val shamt = io.B(4,0).asUInt //提取移位数量,低5bit,因为最多移x^5=32bit

  //MuxLookup：default:io.B
  io.out := MuxLookup(io.alu_op, io.B, Seq(
      ALU_ADD  -> (io.A + io.B),
      ALU_SUB  -> (io.A - io.B),
      ALU_SRA  -> (io.A.asSInt >> shamt).asUInt,   //将UInt转化为Int实行算术移位
      ALU_SRL  -> (io.A >> shamt),
      ALU_SLL  -> (io.A << shamt),
      ALU_SLT  -> (io.A.asSInt < io.B.asSInt),    //将UInt转化为Int实行算术移位
      ALU_SLTU -> (io.A < io.B),
      ALU_AND  -> (io.A & io.B),
      ALU_OR   -> (io.A | io.B),
      ALU_XOR  -> (io.A ^ io.B),
      ALU_COPY_A -> io.A))

  io.sum := io.A + Mux(io.alu_op(0), -io.B, io.B)   //io.alu_op(0)提取最低位,若为1返回A-B否则返回A+B
}

class ALUArea(implicit p: Parameters) extends ALU()(p) {    //功能相同的另一个ALU，实现方式不同
  val sum = io.A + Mux(io.alu_op(0), -io.B, io.B)       //io.alu_op(0)提取最低位,若为1返回A-B否则返回A+B
  val cmp = Mux(io.A(xlen-1) === io.B(xlen-1), sum(xlen-1),
            Mux(io.alu_op(1), io.B(xlen-1), io.A(xlen-1)))
  val shamt  = io.B(4,0).asUInt
  val shin   = Mux(io.alu_op(3), io.A, Reverse(io.A))
  val shiftr = (Cat(io.alu_op(0) && shin(xlen-1), shin).asSInt >> shamt)(xlen-1, 0)
  val shiftl = Reverse(shiftr)

  val out = 
    Mux(io.alu_op === ALU_ADD || io.alu_op === ALU_SUB, sum,
    Mux(io.alu_op === ALU_SLT || io.alu_op === ALU_SLTU, cmp,
    Mux(io.alu_op === ALU_SRA || io.alu_op === ALU_SRL, shiftr,
    Mux(io.alu_op === ALU_SLL, shiftl,
    Mux(io.alu_op === ALU_AND, (io.A & io.B),
    Mux(io.alu_op === ALU_OR,  (io.A | io.B),
    Mux(io.alu_op === ALU_XOR, (io.A ^ io.B), 
    Mux(io.alu_op === ALU_COPY_A, io.A, io.B))))))))


  io.out := out
  io.sum := sum
}
