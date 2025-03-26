#ifndef DFC_HEAD
#define DFC_HEAD

#include <stdbool.h>

#define SIZE 1024
#define LIMIT 5
#define TASK_SIZE 3

int task_argv[SIZE + 2];  // Êý¾Ý
int task_state[SIZE];     // ×´Ì¬
int size = SIZE;

//compute function
int and(int x, int y);
int sub(int x, int y);
int add(int x, int y);

//asm function
void write_table(int*, int);       // wta(listen_addr, pid)
void listen_table(int*);           // lta(listen_addr)

//task node array
int(*task[TASK_SIZE])(int, int);

int scheduler(int x);

void core1();
void core2();

int read_csr() {
    int csr_reg;
    int cpuid;
    asm  volatile(
        "csrr %0, 0xf10;\
          csrr %[out2], 0xf00;"
        : [out1]"=r"(csr_reg), [out2]"=r"(cpuid)
        );
    return csr_reg;
}

#endif