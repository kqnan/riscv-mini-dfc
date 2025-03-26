#include <verilated.h>
#include <iostream>

#if VM_TRACE
# include <verilated_vcd_c.h>	// Trace file format header
#endif

using namespace std;

vluint64_t main_time = 0;       // Current simulation time // 仿真时间戳
                                // This is a 64-bit integer to reduce wrap over issues and
                                // allow modulus.  You can also use a double, if you wish.

double sc_time_stamp () {       // Called by $time in Verilog
  return main_time;             // converts to double, to match
                                // what SystemC does
}

VTile* top;         // target design 顶层设计的C++类
#ifdef VM_TRACE
VerilatedVcdC* tfp; // vcd
#endif

// TODO Provide command-line options like vcd filename, timeout count, etc.
const long timeout = 30000L;

void tick() {
  top->clock = 1;
  top->eval();                               // 仿真时间步进
#if VM_TRACE
  if (tfp) tfp->dump((double) main_time);    // vcd写入
#endif
  main_time++;

  // 外部接口连接,如mem
  // ...
  // ...
  
  top->clock = 0;                            // 时钟翻转
  top->eval();
#if VM_TRACE
  if (tfp) tfp->dump((double) main_time);    // vcd写入
#endif // VM_TRACE
  main_time++;
}

int main(int argc, char** argv) {
  Verilated::commandArgs(argc, argv);         // Remember args
  top = new VTile;                            // target design

#if VM_TRACE			                            // If verilator was invoked with --trace
  Verilated::traceEverOn(true);	              // Verilator must compute traced signals
  VL_PRINTF("Enabling waves...\n");
  tfp = new VerilatedVcdC;
  top->trace(tfp, 99);	                      // Trace 99 levels of hierarchy
  tfp->open(argc > 1 ? argv[1] : "dump.vcd"); // Open the dump file
#endif

  cout << "Starting simulation!\n";

  // reset
  top->reset = 1;
  for (size_t i = 0; i < 10; i++) {
    tick();
  }

  // start
  top->reset = 0;
  top->io_host1_fromhost_bits = 0;
  top->io_host1_fromhost_valid = 0;
  top->io_host2_fromhost_bits = 0;
  top->io_host2_fromhost_valid = 0;
  top->io_rxd = 1;
  int flag1 = 0;                                // 记录tohost的输出
  int flag2 = 0;
  do {
    tick();
    if(top->io_host1_tohost) flag1 = top->io_host1_tohost;
    if(top->io_host2_tohost) flag2 = top->io_host2_tohost;
  } while(!(flag1 && flag2) && main_time < timeout);

  // cout << flag1 << endl;
  // cout << flag2 << endl;
  int retcode1 = flag1 >> 1;
  int retcode2 = flag2 >> 1;                  // 右移1位(写入时左移了1位)；多跑10个时钟？
  // Run for 10 more clocks                     // 可能有没跑完的中断指令，所以tohost中断后多跑几个时钟以便观察
  for (size_t i = 0 ; i < 10 ; i++) {
    tick();
  }

  if (main_time >= timeout) {
    cerr << "Simulation terminated by timeout at time " << main_time
         << " (cycle " << main_time / 10 << ")"<< endl;
    return EXIT_FAILURE;
  } else {
    cerr << "Simulation completed at time " << main_time <<
           " (cycle " << main_time / 10 << ")"<< endl;
    if (retcode1) {
      cerr << "core1：TOHOST = " << retcode1 << endl;
    }
    if (retcode2) {
      cerr << "core2：TOHOST = " << retcode2 << endl;
    }
  }

#if VM_TRACE
  if (tfp) tfp->close();
  delete tfp;
#endif
  delete top;

  cout << "Finishing simulation!\n";

  return (retcode1 == 0 && retcode2 == 0) ? EXIT_SUCCESS : EXIT_FAILURE;
}