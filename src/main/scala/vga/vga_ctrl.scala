package vga

import chisel3._

class vga_ctrl extends Module{
  val io = IO(new Bundle{
    val pix_data= Input(UInt(12.W))
    val pix_x= Output(UInt(10.W))
    val pix_y= Output(UInt(10.W))
    val hsync=Output(UInt(1.W))
    val vsync=Output(UInt(1.W))
    val vga_rgb=Output(UInt(12.W))
  })

  val H_SYNC=96.U;val H_BACK=40.U;val H_LEFT=8.U;val H_VALID=640.U;val H_RIGHT=8.U;val H_FRONT=8.U;val H_TOTAL=800.U;
  val V_SYNC=2.U;val V_BACK=25.U;val V_TOP=8.U;val V_VALID=480.U;val V_BOTTOM=8.U;val V_FRONT=2.U;val V_TOTAL=525.U;

  val cnt_h = RegInit(0.U(10.W))
  val cnt_v = RegInit(0.U(10.W))

  //withClockAndReset(io.vga_clk,io.sys_rst_n){


    when(cnt_h === (H_TOTAL - 1.U)){
      cnt_h:=0.U
    }.otherwise{
      cnt_h:=cnt_h+1.U
    }
    //}
    //  withClockAndReset(io.vga_clk,io.sys_rst_n){
    when((cnt_v===(V_TOTAL-1.U))&&(cnt_h===(H_TOTAL-1.U))){
      cnt_v:=0.U
    }.elsewhen(cnt_h ===(H_TOTAL-1.U)){
      cnt_v:=cnt_v+1.U
    }.otherwise{
      cnt_v:=cnt_v
    }


 // }
//  val rgb_valid= Mux( (cnt_h >=H_SYNC+H_BACK+H_LEFT)
//      &&(cnt_h<H_SYNC+H_BACK+H_LEFT+H_VALID)
//    &&(cnt_v>=V_SYNC+V_BACK+V_TOP)
//    &&(cnt_v<V_SYNC+V_BACK+V_TOP+V_VALID)
//    ,true.B,false.B)
  val rgb_valid=Mux(cnt_h<H_VALID&&cnt_v<V_VALID, true.B,false.B)

//  val pix_data_req = Mux( (cnt_h >=H_SYNC+H_BACK+H_LEFT-1.U)
//    &&(cnt_h<H_SYNC+H_BACK+H_LEFT+H_VALID-1.U)
//    &&(cnt_v>=V_SYNC+V_BACK+V_TOP)
//    &&(cnt_v<V_SYNC+V_BACK+V_TOP+V_VALID)
//    ,true.B,false.B)
val pix_data_req=Mux(rgb_valid, true.B,false.B)

  //io.pix_x:=Mux(pix_data_req,cnt_h-(H_SYNC+H_BACK+H_LEFT-1.U),0x3ff.U)
//  io.pix_x:=Mux(pix_data_req,cnt_h-(H_SYNC+H_BACK+H_LEFT-1.U),0x3ff.U)
//  io.pix_y:=Mux(pix_data_req,cnt_v-(V_SYNC+V_BACK+V_TOP),0x3ff.U)
  io.pix_x:=Mux(pix_data_req,cnt_h,0x3ff.U)
  io.pix_y:=Mux(pix_data_req,cnt_v,0x3ff.U)
//  io.hsync:=Mux(cnt_h<=H_SYNC-1.U,1.U,0.U)
//  io.vsync:=Mux(cnt_v<=V_SYNC-1.U,1.U,0.U)
  io.hsync:=Mux(cnt_h>=H_VALID+H_RIGHT+H_FRONT && cnt_h<H_VALID+H_RIGHT+H_FRONT+H_SYNC,1.U,0.U)
  io.vsync:=Mux(cnt_v>=V_VALID+V_FRONT+V_BOTTOM && cnt_v<V_VALID+V_FRONT+V_BOTTOM +V_SYNC,1.U,0.U)
  io.vga_rgb:=Mux(rgb_valid,io.pix_data,0.U)
}
object emitsss extends App{
  (new chisel3.stage.ChiselStage).emitVerilog(new vga_ctrl())
  (new chisel3.stage.ChiselStage).emitVerilog(new vga_pic())
}