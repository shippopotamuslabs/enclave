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

std::vector<float> blendImg2ImgLatents(
    const std::vector<float>& encoded,
    const std::vector<float>& noise,
    float denoise_strength,
    float sigma) {
    if (encoded.size() != noise.size()) {
        throw std::invalid_argument("img2img latent/noise size mismatch");
    }
    const float strength = std::clamp(denoise_strength, 0.0f, 1.0f);
    const float keep = 1.0f - strength;
    std::vector<float> out(encoded.size());
    for (size_t i = 0; i < out.size(); ++i) {
        out[i] = keep * encoded[i] + strength * sigma * noise[i];
    }
    return out;
}

}  // namespace enclave::diffusion
