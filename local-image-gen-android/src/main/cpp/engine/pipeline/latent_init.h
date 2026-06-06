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

std::vector<float> blendImg2ImgLatents(
    const std::vector<float>& encoded,
    const std::vector<float>& noise,
    float denoise_strength,
    float sigma);

}  // namespace enclave::diffusion
