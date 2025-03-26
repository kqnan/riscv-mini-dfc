// See LICENSE for license details.

package mini

import chisel3._
import chisel3.util._
import junctions._
import freechips.rocketchip.config.{Parameters, Field}

case object NWays extends Field[Int]
case object NSets extends Field[Int]
case object CacheBlockBytes extends Field[Int]

//cache请求
class CacheReq(implicit p: Parameters) extends CoreBundle()(p) {
  val addr = UInt(xlen.W)
  val data = UInt(xlen.W)
  val mask = UInt((xlen/8).W)
}

//cache相应
class CacheResp(implicit p: Parameters) extends CoreBundle()(p) {
  val data = UInt(xlen.W)
}

//一进一出一控制,进出信号被valid包裹
class CacheIO (implicit val p: Parameters) extends Bundle {
  val abort = Input(Bool())
  val req   = Flipped(Valid(new CacheReq))
  val resp  = Valid(new CacheResp)
}

//整个cache模块IO,一方连接cpu,另一方通过nasti接口连接arb
class CacheModuleIO(implicit val p: Parameters) extends Bundle {
  val cpu   = new CacheIO
  val nasti = new NastiIO
}

trait CacheParams extends CoreParams with HasNastiParameters {
  val nWays  = p(NWays) // Not used...    //一路(直接映射)
  val nSets  = p(NSets)                   //256组
  val bBytes = p(CacheBlockBytes)         //cache块大小:16Byte
  val bBits  = bBytes << 3                //每个block的字节数
  val blen   = log2Ceil(bBytes)           //
  val slen   = log2Ceil(nSets)            //index长度:8bit
  val tlen   = xlen - (slen + blen)       //tag长度:
  val nWords = bBits / xlen               //每块字数:4 word
  val wBytes = xlen / 8
  val byteOffsetBits = log2Ceil(wBytes)
  val dataBeats = bBits / nastiXDataBits  //每次传输beats数:2 beat
} 

class MetaData(implicit val p: Parameters) extends Bundle with CacheParams {
  val tag   = UInt(tlen.W)
}

class Cache(implicit val p: Parameters) extends Module with CacheParams {
  //import Chisel._ // FIXME: read enable signals for memories are broken by new chisel
  import Chisel.{SeqMem,Vec}

  val io = IO(new CacheModuleIO)
  // cache states
  val (s_IDLE :: s_READ_CACHE :: s_WRITE_CACHE :: s_WRITE_BACK :: s_WRITE_ACK ::
       s_REFILL_READY :: s_REFILL :: Nil) = Enum(7)
  val state = RegInit(s_IDLE)
  // memory
  val v        = RegInit(0.U(nSets.W))
  val d        = RegInit(0.U(nSets.W))
  val metaMem  = SeqMem(nSets, new MetaData)
  val dataMem  = Seq.fill(nWords)(SeqMem(nSets, Vec(wBytes, UInt(8.W))))

  val addr_reg = Reg(io.cpu.req.bits.addr.cloneType)
  val cpu_data = Reg(io.cpu.req.bits.data.cloneType)
  val cpu_mask = Reg(io.cpu.req.bits.mask.cloneType)

  // Counters
  //当fire时开始计数,长度为dataBeats。返回计数值count以及达到最大值时wrap_out(可用于设置last)
  require(dataBeats == 1)
  val (read_count,  read_wrap_out)  = Counter(io.nasti.r.fire(), dataBeats)
  val (write_count, write_wrap_out) = Counter(io.nasti.w.fire(), dataBeats)

  val is_idle   = state === s_IDLE
  val is_read   = state === s_READ_CACHE
  val is_write  = state === s_WRITE_CACHE
  val is_alloc  = state === s_REFILL && read_wrap_out
  val is_alloc_reg = RegNext(is_alloc)
  
  val hit = Wire(Bool())
  val wen = is_write && (hit || is_alloc_reg) && !io.cpu.abort || is_alloc 
  val ren = !wen && (is_idle || is_read) && io.cpu.req.valid 
  val ren_reg = RegNext(ren)

  val addr     = io.cpu.req.bits.addr
  //提取对应位
  val idx      = 0.U
  val tag_reg  = addr_reg(xlen-1, slen+blen)
  val idx_reg  = 0.U
  val off_reg  = 0.U

  val rmeta = metaMem.read(idx, ren)
  val rdata = Cat((dataMem map (_.read(idx, ren).asUInt)).reverse)
  val rdata_buf = RegEnable(rdata, ren_reg)
  val refill_buf = Reg(Vec(dataBeats, UInt(nastiXDataBits.W)))
  val read = Mux(is_alloc_reg, refill_buf.asUInt, Mux(ren_reg, rdata, rdata_buf))

  hit := v(idx_reg) && rmeta.tag === tag_reg 

  // Read Mux
  //tabulate:返回一个vector,结果为从0到nwords依次作用于给定函数的返回值
  //该部分分别提取read中的各word组成一个vector然后提取第off_reg个
  io.cpu.resp.bits.data := Vec.tabulate(nWords)(i => read((i+1)*xlen-1, i*xlen))(off_reg)
  io.cpu.resp.valid     := is_idle || is_read && hit || is_alloc_reg && !cpu_mask.orR

  when(io.cpu.resp.valid) { 
    addr_reg  := addr
    cpu_data  := io.cpu.req.bits.data
    cpu_mask  := io.cpu.req.bits.mask
  }

  val wmeta = Wire(new MetaData)
  wmeta.tag := tag_reg

  val wmask = Mux(!is_alloc, (cpu_mask << 0.U(byteOffsetBits.W)).zext, -1.S) //SInt(-1) To -1.S
  val wdata = Mux(!is_alloc, Fill(nWords, cpu_data), 
    if (refill_buf.size == 1) io.nasti.r.bits.data
    else Cat(io.nasti.r.bits.data, Cat(refill_buf.init.reverse)))
  when(wen) {
    v := v.bitSet(idx_reg, true.B)
    d := d.bitSet(idx_reg, !is_alloc)
    when(is_alloc) {
      metaMem.write(idx_reg, wmeta)
    }
    dataMem.zipWithIndex foreach { case (mem, i) =>
      val data = Vec.tabulate(wBytes)(k => wdata(i*xlen+(k+1)*8-1, i*xlen+k*8))
      mem.write(idx_reg, data, wmask((i+1)*wBytes-1, i*wBytes).asBools())
      mem suggestName s"dataMem_${i}"
    }
  }
  //id addr size len
  //size:表示方式为2^size
  //nasti中的len为数值+1,因此为dataBeats-1
  io.nasti.ar.bits := NastiReadAddressChannel(
    0.U, tag_reg << blen.U, log2Up(nastiXDataBits/8).U, (dataBeats-1).U)
  io.nasti.ar.valid := false.B
  // read data
  io.nasti.r.ready := state === s_REFILL
  when(io.nasti.r.fire()) { refill_buf(read_count) := io.nasti.r.bits.data }

  // write addr
  io.nasti.aw.bits := NastiWriteAddressChannel(
    0.U, rmeta.tag << blen.U, log2Up(nastiXDataBits/8).U, (dataBeats-1).U)
  io.nasti.aw.valid := false.B
  // write data
  //使用计数器,当w.fire时开始计数
  io.nasti.w.bits := NastiWriteDataChannel(
    Vec.tabulate(dataBeats)(i => read((i+1)*nastiXDataBits-1, i*nastiXDataBits))(write_count),
    None, write_wrap_out)
  io.nasti.w.valid := false.B
  // write resp
  io.nasti.b.ready := false.B

  // Cache FSM
  val is_dirty = v(idx_reg) && d(idx_reg)
  switch(state) {
    is(s_IDLE) {
      when(io.cpu.req.valid) {
        state := Mux(io.cpu.req.bits.mask.orR, s_WRITE_CACHE, s_READ_CACHE)
      }
    }
    is(s_READ_CACHE) {
      when(hit) {
        when(io.cpu.req.valid) {
          state := Mux(io.cpu.req.bits.mask.orR, s_WRITE_CACHE, s_READ_CACHE)
        }.otherwise {
          state := s_IDLE
        }
      }.otherwise {
        io.nasti.aw.valid := is_dirty 
        io.nasti.ar.valid := !is_dirty
        when(io.nasti.aw.fire()) {
          state := s_WRITE_BACK
        }.elsewhen(io.nasti.ar.fire()) {
          state := s_REFILL
        }
      }
    }
    is(s_WRITE_CACHE) {
      when(hit || is_alloc_reg || io.cpu.abort) {
        state := s_IDLE
      }.otherwise {
        io.nasti.aw.valid := is_dirty
        io.nasti.ar.valid := !is_dirty
        when(io.nasti.aw.fire()) {
          state := s_WRITE_BACK
        }.elsewhen(io.nasti.ar.fire()) {
          state := s_REFILL
        }
      }
    }
    is(s_WRITE_BACK) {
      io.nasti.w.valid := true.B
      when(write_wrap_out) {
        state := s_WRITE_ACK
      }
    }
    is(s_WRITE_ACK) {
      io.nasti.b.ready := true.B
      when(io.nasti.b.fire()) {
        state := s_REFILL_READY
      }
    }
    is(s_REFILL_READY) {
      io.nasti.ar.valid := true.B
      when(io.nasti.ar.fire()) {
        state := s_REFILL
      }
    }
    is(s_REFILL) {
      when(read_wrap_out) {
        state := Mux(cpu_mask.orR, s_WRITE_CACHE, s_IDLE) 
      }
    }
  }
}
