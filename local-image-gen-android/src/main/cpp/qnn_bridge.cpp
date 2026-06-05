#include "local_image_gen_backend_client.h"
#include "local_image_gen_runtime.h"

#include <cstring>

extern "C" int qnn_probe_runtime() { return local_image_gen::probe_qnn_libs(); }

extern "C" int qnn_generate_image(
    const char* model_dir,
    const char* prompt,
    const char* negative_prompt,
    int width,
    int height,
    int steps,
    float cfg_scale,
    long seed,
    const char* output_path,
    char* error_out,
    int error_out_len) {
    if (qnn_probe_runtime() < 2) {
        const char* msg =
            "QNN runtime not ready. Run scripts/sync-qnn-jniLibs.sh and "
            "scripts/build-stable-diffusion-core.sh.";
        if (error_out != nullptr && error_out_len > 0) {
            std::strncpy(error_out, msg, static_cast<size_t>(error_out_len - 1));
            error_out[error_out_len - 1] = '\0';
        }
        return -1;
    }

    local_image_gen::BackendLaunchConfig config;
    config.model_dir = model_dir != nullptr ? model_dir : "";
    config.use_cpu = false;

    local_image_gen::GenerateRequest request;
    request.prompt = prompt != nullptr ? prompt : "";
    request.negative_prompt = negative_prompt != nullptr ? negative_prompt : "";
    request.width = width;
    request.height = height;
    request.steps = steps;
    request.cfg_scale = cfg_scale;
    request.seed = seed;
    request.output_path = output_path != nullptr ? output_path : "";

    return local_image_gen::run_stable_diffusion_generate(config, request, error_out, error_out_len)
               ? 0
               : -1;
}