#pragma once

#include <string>

namespace local_image_gen {

struct BackendLaunchConfig {
    std::string model_dir;
    bool use_cpu = false;
};

struct GenerateRequest {
    std::string prompt;
    std::string negative_prompt;
    int width = 512;
    int height = 512;
    int steps = 20;
    float cfg_scale = 7.5f;
    long seed = 0;
    std::string output_path;
};

bool run_stable_diffusion_generate(
    const BackendLaunchConfig& config,
    const GenerateRequest& request,
    char* error_out,
    int error_out_len);

}  // namespace local_image_gen