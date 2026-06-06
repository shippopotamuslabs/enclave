#include "engine/schedulers/scheduler.h"

#include <cstdio>

int main() {
    enclave::diffusion::Scheduler* scheduler = enclave::diffusion::createScheduler("lcm", true);
    scheduler->setTimesteps(16);
    if (scheduler->numTimesteps() != 16) {
        std::fprintf(stderr, "expected 16 LCM steps\n");
        return 1;
    }
    delete scheduler;
    std::printf("scheduler_lcm_test passed\n");
    return 0;
}
