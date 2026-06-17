#include "engine/schedulers/scheduler.h"

#include <cstdio>

int main() {
    enclave::diffusion::Scheduler* scheduler = enclave::diffusion::createScheduler("dpm", false);
    scheduler->setTimesteps(20);
    if (scheduler->numTimesteps() != 20) {
        std::fprintf(stderr, "expected 20 DPM steps\n");
        return 1;
    }
    if (scheduler->timestepAt(0) <= scheduler->timestepAt(19)) {
        std::fprintf(stderr, "DPM timesteps should descend\n");
        return 1;
    }
    delete scheduler;
    std::printf("scheduler_dpm_test passed\n");
    return 0;
}
