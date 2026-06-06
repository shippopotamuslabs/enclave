#pragma once

#include "engine/include/enclave_diffusion.h"
#include "engine/runners/qnn_graph_runner.h"

#include <string>

namespace enclave::harness {

struct HarnessOptions {
    std::string model_dir;
    std::string qnn_runtime_dir;
    std::string dsp_library_path;
    std::string prompt = "a photo of a cat";
    std::string negative_prompt;
    std::string output_png = "/tmp/enclave-harness-out.png";
    std::string init_image_path;
    std::string step = "all";
    EnclaveModelFamily family = ENCLAVE_MODEL_SD15_QNN;
    bool family_explicit = false;
    int width = 0;
    int height = 0;
    int steps = 0;
    float cfg_scale = 0.0f;
    float denoise_strength = 0.75f;
    std::string scheduler;
    int64_t seed = 42;
    std::string resolved_backend_path;
    std::string resolved_system_path;
};

bool parseArgs(int argc, char** argv, HarnessOptions& out, std::string& error);

enclave::diffusion::QnnRuntimePaths buildQnnPaths(const HarnessOptions& options);

EnclaveDiffusionConfig buildDiffusionConfig(HarnessOptions& options);

void printUsage(const char* program);

}  // namespace enclave::harness
