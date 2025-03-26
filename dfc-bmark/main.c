#include "main.h"
#include <stdbool.h>

void dfc_init(int num) {
    //dfc ready
    for (int i = 0; i < num; i++) {
        write_table(task_argv + i, i + 1);
        write_table(task_argv + i + 1, i + 1);
        task_state[i] = 0;
    }
    for (int i = 0; i < num + 2; i++) {
        task_argv[i] = 2;
        listen_table(task_argv + i);
    }
    task[0] = and;
    task[1] = sub;
    task[2] = add;
}

int and(int x, int y) { return x & y; }
int sub(int x, int y) { return x - y; }
int scheduler(int x) { return task[0](task_argv[x], task_argv[x + 1]); }

void core1() {
    dfc_init(10);

    //task_argv[0] = 1;
    //task_argv[1] = 2;
    //task_state[0] = 1;
    ////task_argv[2] = 3;
    ////task_argv[3] = 4;
    ////task_argv[4] = 5;
    ////int j = 0;
    ////while (j <= LIMIT) {
    ////    for (int k = 0; k < SIZE; ++k) {
    ////        task_argv[k + 2] = k + 3;
    ////    }
    ////    j++;
    ////}

    //// start sim
    //int fail_cnt = 0, schedu_sum = 0;
    //while (fail_cnt <= LIMIT || schedu_sum < SIZE) {
    //    bool get_task = false;
    //    for (int k = 0; k < SIZE; ++k) {
    //        if (1 == task_state[k]) {
    //            ++schedu_sum; get_task = true; task_state[k] = 2;
    //            int x = scheduler(k);
    //            task_argv[k + 2] = x;
    //            task_state[k + 1] = 1;
    //        }
    //    }
    //    fail_cnt += (false == get_task);
    //}
    return;
}

void core2() {
    // dfc_init(6);
    return;
}

int main(int argc, char** argv) {
    int hartid = read_csr();  // CSR_MHARTID 0xf10
    if (hartid == 0) {
        core1();
    }
    else if (hartid == 1) {
        core2();
    }
    else {
        return -1;
    }
    return 0;
}

















