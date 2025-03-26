
package mini

import chisel3.iotesters.{Driver, PeekPokeTester}
import chisel3.testers.BasicTester
import chisel3._
import chisel3.util._
import chisel3.testers._
import org.scalatest.{FlatSpec, Matchers}

// test step
// $ make sbt
// $ testOnly TableDTests

class TableDTester (dut: DFC_TableD) extends PeekPokeTester(dut) {
  val data_Addr = 0x46403377
  val data_tableAid = 0x0a
  val hitListenAddr: Int = data_Addr
  val Info_data_t = 0x0000024C

  val wlistenAddr = Seq(data_Addr, 0, 0, 0, 0, 0, 0, 0, 0, 0)
  val wtableAId = Seq(data_tableAid, 0, 0, 0, 0, 0, 0, 0, 0, 0)
  val wValid = Seq(1, 0, 0, 0, 0, 0, 0, 0)

  val llistenAddr = Seq(0, 0, 0, 0, hitListenAddr, 0, 0, 0, 0, 0)
  val lValid = Seq(0, 0, 0, 0, 1, 0, 0, 0, 0, 0)

  val wrespReady = Seq(1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
  val lrespReady = Seq(1, 1, 1, 1, 1, 1, 1, 1, 1, 1)

  // except value
  // val wXXX_expect = Seq(0, 0, 0, 0, 0, 0, 0, 0)

  println("Testing singal step length = " + wValid.length)

  for(i <- wValid.indices){
    poke(dut.io.tableA.wdata.bits.listenAddr, wlistenAddr(i))
    poke(dut.io.tableA.wdata.bits.tableAId, wtableAId(i))
    poke(dut.io.tableA.wdata.valid, wValid(i))

    poke(dut.io.tableA.ldata.bits.listenAddr, llistenAddr(i))
    poke(dut.io.tableA.ldata.valid, lValid(i))

    poke(dut.io.tableA.wresp.ready, wrespReady(i))
    poke(dut.io.tableA.lresp.ready, lrespReady(i))
    step(1)

    // test
    // expect(dut.io.tableA.XXX, wXXX_expect(i))
    if(peek(dut.io.tableA.wresp.valid) == BigInt(1)){
      expect(dut.io.tableA.wresp.bits, BigInt(0))
    }
    if(peek(dut.io.tableA.lresp.valid) == BigInt(1) && peek(dut.io.tableA.lresp.bits.finish) == BigInt(0)){
      expect(dut.io.tableA.lresp.bits.tableAId, data_tableAid)
    }
  }

  step(1)
}

class TableDTests extends FlatSpec with Matchers {
  "DFC_TableD" should "pass" in {
    implicit val p = (new MiniConfig).toInstance
    Driver.execute(Array(), () => new DFC_TableD){ 
      c => new TableDTester(c)
    } should be (true)
  }
}

