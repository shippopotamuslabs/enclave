#include "enclave_diffusion.h"
#include "local_image_gen_runtime.h"

#include <cstring>
#include <mutex>
#include <string>

namespace {
std::mutex g_engine_mutex;
EnclaveDiffusionHandle* g_engine = nullptr;
std::string g_engine_model_dir;

EnclaveDiffusionHandle* ensureEngine(const char* model_dir, char* error_out, int error_out_len) {
    const std::string dir = model_dir != nullptr ? model_dir : "";
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_engine != nullptr && g_engine_model_dir == dir) {
        return g_engine;
    }
    if (g_engine != nullptr) {
        enclave_diffusion_destroy(g_engine);
        g_engine = nullptr;
    }
    const bool sdxl = local_image_gen::file_exists_in_dir(dir, "clip_2.mnn");
    EnclaveDiffusionConfig config {};
    config.model_dir = dir.c_str();
    config.family = sdxl ? ENCLAVE_MODEL_SDXL_QNN : ENCLAVE_MODEL_SD15_QNN;
    const std::string runtime = local_image_gen::qnn_runtime_dir();
    const std::string backend = runtime + "/libQnnHtp.so";
    const std::string system = runtime + "/libQnnSystem.so";
    config.qnn_backend_path = backend.c_str();
    config.qnn_system_path = system.c_str();
    config.dsp_library_path = runtime.c_str();
    config.native_lib_dir = local_image_gen::native_lib_dir().c_str();
    g_engine = enclave_diffusion_create(&config, error_out, error_out_len);
    if (g_engine != nullptr) {
        g_engine_model_dir = dir;
    }
    return g_engine;
}

}  // namespace

extern "C" int qnn_probe_runtime() { return local_image_gen::probe_qnn_libs(); }

extern "C" int qnn_generate_image(
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
    if (qnn_probe_runtime() < 2) {
        const char* msg =
            "QNN runtime not ready. Run scripts/sync-qnn-jniLibs.sh.";
        if (error_out != nullptr && error_out_len > 0) {
            std::strncpy(error_out, msg, static_cast<size_t>(error_out_len - 1));
            error_out[error_out_len - 1] = '\0';
        }
        return -1;
    }
    if (!local_image_gen::qnn_model_dir_ready(model_dir, error_out, error_out_len)) {
        return -1;
    }
    if (!local_image_gen::prepare_qnn_runtime_libs(error_out, error_out_len)) {
        return -1;
    }

    EnclaveDiffusionHandle* engine = ensureEngine(model_dir, error_out, error_out_len);
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

extern "C" void qnn_cancel_generation() {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_engine != nullptr) {
        enclave_diffusion_cancel(g_engine);
    }
}

extern "C" void qnn_on_trim_memory(int level) {
    std::lock_guard<std::mutex> lock(g_engine_mutex);
    if (g_engine != nullptr) {
        enclave_diffusion_on_trim_memory(g_engine, level);
    }
}
