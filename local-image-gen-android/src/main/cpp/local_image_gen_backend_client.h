#pragma once

#include <string>

namespace local_image_gen {

struct GenerateRequest {
    int width = 1024;
    int height = 1024;
    int steps = 20;
    float cfg_scale = 7.5f;
    const char* scheduler = "dpm";
};

inline std::string build_generate_request_body(const GenerateRequest& request) {
    const char* scheduler = request.scheduler != nullptr ? request.scheduler : "dpm";
    return std::string(R"({"width":)") + std::to_string(request.width) +
        R"(,"height":)" + std::to_string(request.height) +
        R"(,"steps":)" + std::to_string(request.steps) +
        R"(,"cfg_scale":)" + std::to_string(request.cfg_scale) +
        R"(,"scheduler":")" + scheduler + R"("})";
}

}  // namespace local_image_gen
