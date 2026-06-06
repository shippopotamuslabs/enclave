#pragma once

#include <string>
#include <vector>

namespace enclave::diffusion {

struct Image {
    int width = 0;
    int height = 0;
    int channels = 3;
    std::vector<float> pixels;
};

bool writePngRgbFloat(
    const std::string& path,
    const std::vector<float>& chw,
    int width,
    int height,
    std::string& error);

bool readImageRgbFloat(
    const std::string& path,
    int target_width,
    int target_height,
    Image& image,
    std::string& error);

std::vector<float> makeFallbackPreview(
    const std::vector<float>& latents,
    int width,
    int height);

}  // namespace enclave::diffusion
