// See LICENSE for license details.

package mini

import java.io.{File, FileWriter}
import chisel3._
import chisel3.stage.{ChiselStage, ChiselGeneratorAnnotation}

object Main extends App {
//  val dir = new File(args(0)) ; dir.mkdirs
  val params = (new MiniConfig).toInstance
//  val chirrtl = firrtl.Parser.parse(chisel3.Driver.emit(() => new Tile(params)))
//  val writer = new FileWriter(new File(dir, s"${chirrtl.main}.fir"))
//  writer write chirrtl.serialize
//  writer.close
//
//  val verilog = new FileWriter(new File(dir, s"${chirrtl.main}.v"))
//  verilog write (new firrtl.VerilogCompiler).compileAndEmit(firrtl.CircuitState(chirrtl, firrtl.ChirrtlForm)).getEmittedCircuit.value
//  verilog.close
  // chisel3.Driver.execute(args, () => new Tile(params))
  (new chisel3.stage.ChiselStage).emitVerilog(new Tile(params),args)
}
