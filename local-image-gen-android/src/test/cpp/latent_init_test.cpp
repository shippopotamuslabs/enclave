#include "engine/pipeline/latent_init.h"
#include "engine/schedulers/scheduler.h"

#include <vector>

#include <cmath>
#include <cstdio>

int main() {
  enclave::diffusion::Scheduler* scheduler = enclave::diffusion::createScheduler("dpm", false);
  scheduler->setTimesteps(20);
  const std::vector<float> latent(4, 1.0f);
  const std::vector<float> noise(4, 0.0f);
  const auto unchanged =
      enclave::diffusion::blendImg2ImgLatents(latent, noise, 0.0f, scheduler->sigmaAt(0));
  if (std::abs(unchanged[0] - 1.0f) > 1e-3f) {
    std::fprintf(stderr, "denoise 0 should preserve latent\n");
    return 1;
  }
  delete scheduler;
  std::printf("latent_init_test passed\n");
  return 0;
}
