
package mini

import chisel3.iotesters.{Driver, PeekPokeTester}
import chisel3.testers.BasicTester
import chisel3._
import chisel3.util._
import chisel3.testers._
import org.scalatest.{FlatSpec, Matchers}
import vga.testCPU

// test step
// $ make sbt
// $ testOnly TableDTests

class vga (dut: testCPU) extends PeekPokeTester(dut) {
  step(10)
}

class testvga extends FlatSpec with Matchers {
  "vga" should "pass" in {

    Driver.execute(Array(), () => new testCPU){
      c => new vga(c)
    } should be (true)
  }
}

