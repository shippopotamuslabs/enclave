#include "latent_init.h"

#include <algorithm>
#include <cmath>
#include <random>
#include <stdexcept>

namespace enclave::diffusion {

namespace {

size_t latentElementCount(const LatentShape& shape) {
    if (shape.batch <= 0 || shape.channels <= 0 || shape.height <= 0 || shape.width <= 0) {
        throw std::invalid_argument("latent shape dimensions must be positive");
    }
    return static_cast<size_t>(shape.batch) * static_cast<size_t>(shape.channels) *
           static_cast<size_t>(shape.height) * static_cast<size_t>(shape.width);
}

}  // namespace

std::vector<float> generateGaussianLatents(const LatentShape& shape, int64_t seed, float sigma) {
    std::mt19937_64 rng(static_cast<uint64_t>(seed));
    std::normal_distribution<float> normal(0.0f, sigma);
    std::vector<float> latents(latentElementCount(shape));
    for (float& value : latents) {
        value = normal(rng);
    }
    return latents;
}

int img2imgStartStep(int num_inference_steps, float denoise_strength) {
    if (num_inference_steps <= 0) {
        return 0;
    }
    const float strength = std::clamp(denoise_strength, 0.0f, 1.0f);
    if (strength >= 1.0f) {
        return 0;
    }
    if (strength <= 0.0f) {
        return std::max(num_inference_steps - 1, 0);
    }
    const int init_timestep =
        std::min(num_inference_steps - 1, static_cast<int>(num_inference_steps * strength));
    return std::max(num_inference_steps - init_timestep - 1, 0);
}

}  // namespace enclave::diffusion
