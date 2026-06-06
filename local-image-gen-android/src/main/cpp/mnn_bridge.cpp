#include "engine/include/enclave_diffusion.h"
#include "local_image_gen_runtime.h"

#include <cstring>
#include <mutex>
#include <string>

namespace {

std::mutex g_mnn_engine_mutex;
EnclaveDiffusionHandle* g_mnn_engine = nullptr;
std::string g_mnn_engine_model_dir;

void copyError(const char* message, char* error_out, int error_out_len) {
    if (error_out == nullptr || error_out_len <= 0) {
        return;
    }
    std::strncpy(error_out, message, static_cast<size_t>(error_out_len - 1));
    error_out[error_out_len - 1] = '\0';
}

EnclaveDiffusionHandle* ensureMnnEngine(const char* model_dir, char* error_out, int error_out_len) {
    const std::string dir = model_dir != nullptr ? model_dir : "";
    std::lock_guard<std::mutex> lock(g_mnn_engine_mutex);
    if (g_mnn_engine != nullptr && g_mnn_engine_model_dir == dir) {
        return g_mnn_engine;
    }
    if (g_mnn_engine != nullptr) {
        enclave_diffusion_destroy(g_mnn_engine);
        g_mnn_engine = nullptr;
    }
    EnclaveDiffusionConfig config {};
    config.model_dir = dir.c_str();
    config.family = ENCLAVE_MODEL_SD15_MNN;
    config.native_lib_dir = local_image_gen::native_lib_dir().c_str();
    g_mnn_engine = enclave_diffusion_create(&config, error_out, error_out_len);
    if (g_mnn_engine != nullptr) {
        g_mnn_engine_model_dir = dir;
    }
    return g_mnn_engine;
}

}  // namespace

extern "C" int mnn_probe_runtime() { return local_image_gen::probe_mnn_libs(); }

extern "C" int mnn_generate_image(
    const char* model_dir,
    const char* prompt,
    const char* negative_prompt,
    int width,
    int height,
    int steps,
    float cfg_scale,
    const char* scheduler,
    long seed,
    const char* output_path,
    int mode,
    float denoise_strength,
    const char* init_image_path,
    int memory_policy,
    char* error_out,
    int error_out_len) {
    if (mnn_probe_runtime() < 1) {
        copyError("MNN runtime not ready for local image generation.", error_out, error_out_len);
        return -1;
    }
    EnclaveDiffusionHandle* engine = ensureMnnEngine(model_dir, error_out, error_out_len);
    if (engine == nullptr) {
        return -1;
    }

    EnclaveGenerateParams params {};
    params.mode = mode == 1 ? ENCLAVE_DIFFUSION_MODE_IMG2IMG : ENCLAVE_DIFFUSION_MODE_TXT2IMG;
    params.prompt = prompt != nullptr ? prompt : "";
    params.negative_prompt = negative_prompt != nullptr ? negative_prompt : "";
    params.width = width;
    params.height = height;
    params.steps = steps;
    params.cfg_scale = cfg_scale;
    params.scheduler = scheduler != nullptr ? scheduler : "dpm";
    params.seed = seed;
    params.denoise_strength = denoise_strength;
    params.init_image_path = init_image_path;
    params.memory_policy =
        memory_policy == 0 ? ENCLAVE_MEMORY_POLICY_FULL : ENCLAVE_MEMORY_POLICY_LOW_RAM;
    params.output_png_path = output_path != nullptr ? output_path : "";
    return enclave_diffusion_generate(engine, &params, nullptr, nullptr, error_out, error_out_len);
}
