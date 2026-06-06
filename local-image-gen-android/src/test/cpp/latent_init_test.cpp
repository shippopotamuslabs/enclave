#include "engine/pipeline/latent_init.h"
#include "engine/schedulers/scheduler.h"

#include <cmath>
#include <cstdio>
#include <vector>

int main() {
    if (enclave::diffusion::img2imgStartStep(8, 1.0f) != 0) {
        std::fprintf(stderr, "strength 1.0 should start at step 0\n");
        return 1;
    }
    if (enclave::diffusion::img2imgStartStep(8, 0.75f) != 1) {
        std::fprintf(stderr, "strength 0.75 with 8 steps should start at step 1\n");
        return 1;
    }
    if (enclave::diffusion::img2imgStartStep(20, 0.75f) != 4) {
        std::fprintf(stderr, "strength 0.75 with 20 steps should start at step 4\n");
        return 1;
    }

    enclave::diffusion::Scheduler* scheduler = enclave::diffusion::createScheduler("lcm", true);
    scheduler->setTimesteps(8);
    const std::vector<float> z0(4, 2.0f);
    const std::vector<float> noise(4, 1.0f);
    const int start = enclave::diffusion::img2imgStartStep(8, 0.75f);
    const float noise_t = static_cast<float>(scheduler->timestepAt(start)) / 1000.0f;
    const auto noisy = scheduler->addNoise(z0, noise, noise_t);
    if (std::abs(noisy[0] - z0[0]) < 1e-3f) {
        std::fprintf(stderr, "addNoise at img2img start should perturb latent\n");
        return 1;
    }
    delete scheduler;

    std::printf("latent_init_test passed\n");
    return 0;
}
