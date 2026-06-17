#include "image_io.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <string>
#include <vector>

#define STB_IMAGE_IMPLEMENTATION
#include "stb_image.h"

#define STB_IMAGE_WRITE_IMPLEMENTATION
#include "thirdparty/stb_image_write.h"

namespace enclave::diffusion {
namespace {

uint8_t toByte(float value) {
    const float normalized = std::clamp((value + 1.0f) * 0.5f, 0.0f, 1.0f);
    return static_cast<uint8_t>(std::lround(normalized * 255.0f));
}

float normalizePixel(uint8_t value) {
    return (static_cast<float>(value) / 127.5f) - 1.0f;
}

float sampleChannel(
    const uint8_t* rgb,
    int src_width,
    int src_height,
    int channel,
    float x,
    float y) {
    const float clamped_x = std::clamp(x, 0.0f, static_cast<float>(src_width - 1));
    const float clamped_y = std::clamp(y, 0.0f, static_cast<float>(src_height - 1));
    const int x0 = static_cast<int>(std::floor(clamped_x));
    const int y0 = static_cast<int>(std::floor(clamped_y));
    const int x1 = std::min(x0 + 1, src_width - 1);
    const int y1 = std::min(y0 + 1, src_height - 1);
    const float tx = clamped_x - static_cast<float>(x0);
    const float ty = clamped_y - static_cast<float>(y0);

    auto at = [&](int px, int py) -> float {
        const size_t idx = static_cast<size_t>((py * src_width + px) * 3 + channel);
        return normalizePixel(rgb[idx]);
    };
    const float top = at(x0, y0) * (1.0f - tx) + at(x1, y0) * tx;
    const float bottom = at(x0, y1) * (1.0f - tx) + at(x1, y1) * tx;
    return top * (1.0f - ty) + bottom * ty;
}

std::vector<float> resizeRgbToChw(
    const uint8_t* rgb,
    int src_width,
    int src_height,
    int dst_width,
    int dst_height) {
    std::vector<float> chw(static_cast<size_t>(dst_width * dst_height * 3));
    const size_t plane = static_cast<size_t>(dst_width * dst_height);
    for (int y = 0; y < dst_height; ++y) {
        const float src_y = (static_cast<float>(y) + 0.5f) * static_cast<float>(src_height) /
                              static_cast<float>(dst_height) - 0.5f;
        for (int x = 0; x < dst_width; ++x) {
            const float src_x = (static_cast<float>(x) + 0.5f) * static_cast<float>(src_width) /
                                static_cast<float>(dst_width) - 0.5f;
            const size_t dst_idx = static_cast<size_t>(y * dst_width + x);
            for (int c = 0; c < 3; ++c) {
                chw[c * plane + dst_idx] = sampleChannel(rgb, src_width, src_height, c, src_x, src_y);
            }
        }
    }
    return chw;
}

}  // namespace

bool writePngRgbFloat(
    const std::string& path,
    const std::vector<float>& chw,
    int width,
    int height,
    std::string& error) {
    if (path.empty()) {
        error = "Output PNG path is empty.";
        return false;
    }
    if (width <= 0 || height <= 0 || chw.size() < static_cast<size_t>(width * height * 3)) {
        error = "Invalid RGB image tensor.";
        return false;
    }
    std::vector<uint8_t> interleaved(static_cast<size_t>(width * height * 3));
    const size_t plane = static_cast<size_t>(width * height);
    for (size_t i = 0; i < plane; ++i) {
        interleaved[i * 3 + 0] = toByte(chw[i]);
        interleaved[i * 3 + 1] = toByte(chw[plane + i]);
        interleaved[i * 3 + 2] = toByte(chw[2 * plane + i]);
    }
    if (stbi_write_png(path.c_str(), width, height, 3, interleaved.data(), width * 3) == 0) {
        error = "Failed to write PNG output.";
        return false;
    }
    return true;
}

bool readImageRgbFloat(
    const std::string& path,
    int target_width,
    int target_height,
    Image& image,
    std::string& error) {
    image = Image{};
    if (path.empty()) {
        error = "Input image path is empty.";
        return false;
    }
    if (target_width <= 0 || target_height <= 0) {
        error = "Target image dimensions must be positive.";
        return false;
    }

    int src_width = 0;
    int src_height = 0;
    int channels = 0;
    uint8_t* pixels = stbi_load(path.c_str(), &src_width, &src_height, &channels, 3);
    if (pixels == nullptr) {
        error = std::string("Failed to decode input image: ") + stbi_failure_reason();
        return false;
    }
    if (src_width <= 0 || src_height <= 0) {
        stbi_image_free(pixels);
        error = "Decoded image has invalid dimensions.";
        return false;
    }

    image.width = target_width;
    image.height = target_height;
    image.channels = 3;
    image.pixels = resizeRgbToChw(pixels, src_width, src_height, target_width, target_height);
    stbi_image_free(pixels);
    return true;
}

std::vector<float> makeFallbackPreview(
    const std::vector<float>& latents,
    int width,
    int height) {
    std::vector<float> rgb(static_cast<size_t>(width * height * 3), 0.0f);
    if (width <= 0 || height <= 0 || latents.empty()) {
        return rgb;
    }
    const size_t plane = static_cast<size_t>(width * height);
    for (size_t y = 0; y < static_cast<size_t>(height); ++y) {
        for (size_t x = 0; x < static_cast<size_t>(width); ++x) {
            const size_t i = y * static_cast<size_t>(width) + x;
            const float l = latents[i % latents.size()];
            rgb[i] = std::sin(l);
            rgb[plane + i] = std::cos(l * 0.7f);
            rgb[2 * plane + i] = std::sin((static_cast<float>(x + y) / 32.0f) + l * 0.25f);
        }
    }
    return rgb;
}

}  // namespace enclave::diffusion
