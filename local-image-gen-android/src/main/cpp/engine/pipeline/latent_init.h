#pragma once

#include <cstdint>
#include <vector>

namespace enclave::diffusion {

struct LatentShape {
    int batch = 1;
    int channels = 4;
    int height = 64;
    int width = 64;
};

std::vector<float> generateGaussianLatents(const LatentShape& shape, int64_t seed, float sigma);

/** First UNet step index for img2img; matches diffusers strength-based schedule truncation. */
int img2imgStartStep(int num_inference_steps, float denoise_strength);

}  // namespace enclave::diffusion
