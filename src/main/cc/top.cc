#include <verilated.h>
#include <iostream>

#if VM_TRACE
# include <verilated_vcd_c.h>	// Trace file format header
#endif

#include "mm.h"

using namespace std;

vluint64_t main_time = 0;       // Current simulation time
        // This is a 64-bit integer to reduce wrap over issues and
        // allow modulus.  You can also use a double, if you wish.

// vluint64_t start_time = 235500000L;                // begin : vcd
// vluint64_t end_time = start_time + 300000;         // end : vcd
vluint64_t start_time = 0;                // begin : vcd
vluint64_t end_time = 0;         // end : vcd
vluint64_t run_time = 0;
vluint64_t time_cnt = 2;         // end : vcd
bool once = false;                // avoid write start_time twice

double sc_time_stamp () { // Called by $time in Verilog
  return main_time;       // converts to double, to match
                          // what SystemC does
}

VTile* top; // target design
#ifdef VM_TRACE
VerilatedVcdC* tfp;
#endif
mm_magic_t* mem; // target memory

// TODO Provide command-line options like vcd filename, timeout count, etc.
const long timeout = 1600000000L;

void tick() {
  top->clock = 1;
  top->eval();
#if VM_TRACE
  // if (tfp && main_time>=start_time && main_time<=end_time) tfp->dump((double) main_time-start_time);
  if (tfp && once) { tfp->dump((double) time_cnt); time_cnt++;}
#endif // VM_TRACE
  main_time++;

  top->io_nasti_aw_ready = mem->aw_ready();
  top->io_nasti_ar_ready = mem->ar_ready();
  top->io_nasti_w_ready = mem->w_ready();
  top->io_nasti_b_valid = mem->b_valid();
  top->io_nasti_b_bits_id = mem->b_id();
  top->io_nasti_b_bits_resp = mem->b_resp();
  top->io_nasti_r_valid = mem->r_valid();
  top->io_nasti_r_bits_id = mem->r_id();
  top->io_nasti_r_bits_resp = mem->r_resp();
  top->io_nasti_r_bits_last = mem->r_last();
  memcpy(&top->io_nasti_r_bits_data, mem->r_data(), 8);

  mem->tick(
    top->reset,
    top->io_nasti_ar_valid,
    top->io_nasti_ar_bits_addr,
    top->io_nasti_ar_bits_id,
    top->io_nasti_ar_bits_size,
    top->io_nasti_ar_bits_len,

    top->io_nasti_aw_valid,
    top->io_nasti_aw_bits_addr,
    top->io_nasti_aw_bits_id,
    top->io_nasti_aw_bits_size,
    top->io_nasti_aw_bits_len,

    top->io_nasti_w_valid,
    top->io_nasti_w_bits_strb,
    &top->io_nasti_w_bits_data,
    top->io_nasti_w_bits_last,

    top->io_nasti_r_ready,
    top->io_nasti_b_ready
  );
  
  top->clock = 0;
  top->eval();
#if VM_TRACE
  // if (tfp && main_time>=start_time && main_time<=end_time) tfp->dump((double) main_time-start_time);
  if (tfp && once) { tfp->dump((double) time_cnt); time_cnt++;}
#endif // VM_TRACE
  main_time++;
}

int main(int argc, char** argv) {
  Verilated::commandArgs(argc, argv);   // Remember args
  top = new VTile; // target design
  cout << "create mem" << endl;
  mem = new mm_magic_t(4294967295L, 8); // target memory
  cout << "load memory" << endl;
  load_elf((const char*)(argv[1]),mem->get_data());
  // load_mem(0x80000000L,mem->get_data(), (const char*)(argv[1])); // load hex
  cout << "load memory finished" << endl;

#if VM_TRACE			// If verilator was invoked with --trace
  Verilated::traceEverOn(true);	// Verilator must compute traced signals
  VL_PRINTF("Enabling waves...\n");
  tfp = new VerilatedVcdC;
  top->trace(tfp, 99);	// Trace 99 levels of hierarchy
  tfp->open(argc > 2 ? argv[2] : "dump.vcd"); // Open the dump file
#endif

  cout << "Starting simulation!\n";

  // reset
  top->reset = 1;
  for (size_t i = 0; i < 5 ; i++) {
    tick();
  }

  // start
  top->reset = 0;
  top->io_host_fromhost_bits = 0;
  top->io_host_fromhost_valid = 0;
  do {
    tick();
    if (top->io_host_tohost == 0x13 && start_time == 0) { 
      start_time = main_time;

      cerr << "\nstart_time = " << start_time << endl;
    }  
    if (top->io_host_tohost == 0x15 && start_time != 0) { 
      run_time = main_time - start_time;
      cerr << "\nrun_time = " << run_time << endl;

      start_time = 0; break;
    }

    if (top->io_host_tohost == 0x17) {  break; }
  } while(main_time < timeout);
  // cerr << "main_time = " << main_time << endl;

  int retcode = top->io_host_tohost;

  // Run for 10 more clocks
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
    if (retcode) {
      cerr << "TOHOST = " << retcode << endl;
    }
  }

#if VM_TRACE
  if (tfp) tfp->close();
  delete tfp;
#endif
  delete top;
  delete mem;

  cout << "Finishing simulation!\n";

  return retcode == 0 ? EXIT_SUCCESS : EXIT_FAILURE;
}

