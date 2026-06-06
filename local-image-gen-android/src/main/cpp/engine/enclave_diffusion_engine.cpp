#include "engine/include/enclave_diffusion.h"

#include "engine/memory/stage_loader.h"
#include "engine/model_layout/pack_manifest.h"
#include "engine/pipeline/image_io.h"
#include "engine/pipeline/latent_init.h"
#include "engine/runners/mnn_clip_runner.h"
#include "engine/runners/qnn_graph_runner.h"
#include "engine/runners/tokenizer_runner.h"
#include "engine/schedulers/scheduler.h"

#include <algorithm>
#include <atomic>
#include <cstring>
#include <cmath>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace enclave::diffusion {
namespace {

void copyError(const std::string& message, char* err, int err_len) {
    if (err == nullptr || err_len <= 0) {
        return;
    }
    std::strncpy(err, message.c_str(), static_cast<size_t>(err_len - 1));
    err[err_len - 1] = '\0';
}

bool isSdxl(EnclaveModelFamily family) {
    return family == ENCLAVE_MODEL_SDXL_QNN;
}

bool isQnn(EnclaveModelFamily family) {
    return family == ENCLAVE_MODEL_SD15_QNN || family == ENCLAVE_MODEL_SDXL_QNN;
}

int defaultWidth(EnclaveModelFamily family) {
    return isSdxl(family) ? 1024 : 512;
}

int defaultSteps(EnclaveModelFamily family) {
    return isSdxl(family) ? 8 : 20;
}

float defaultCfg(EnclaveModelFamily family) {
    return isSdxl(family) ? 1.5f : 7.5f;
}

float vaeScale(EnclaveModelFamily family) {
    return isSdxl(family) ? 0.13025f : 0.18215f;
}

const char* defaultScheduler(EnclaveModelFamily family) {
    return isSdxl(family) ? "lcm" : "dpm";
}

}  // namespace

}  // namespace enclave::diffusion

struct EnclaveDiffusionHandle {
    explicit EnclaveDiffusionHandle(const EnclaveDiffusionConfig& in_config) : config(in_config) {}

    EnclaveDiffusionConfig config {};
    enclave::diffusion::PackManifest manifest {};
    enclave::diffusion::QnnRuntimePaths qnn_paths {};
    enclave::diffusion::TokenizerRunner tokenizer;
    enclave::diffusion::MnnClipRunner clip;
    enclave::diffusion::QnnGraphRunner qnn;
    std::atomic<bool> cancelled {false};
    /** Trim requested while generate holds mutex; applied when the lock is free. */
    std::atomic<int> pending_trim_level {-1};
    std::mutex mutex;
};

namespace enclave::diffusion {
namespace {

bool validateCreateConfig(
    const EnclaveDiffusionConfig* cfg,
    PackManifest& manifest,
    std::string& error) {
    if (cfg == nullptr || cfg->model_dir == nullptr || cfg->model_dir[0] == '\0') {
        error = "Diffusion model directory is empty.";
        return false;
    }
    if (!loadPackManifest(cfg->model_dir, manifest) || !hasRequiredArtifacts(manifest)) {
        error = "Diffusion model directory is missing required artifacts.";
        return false;
    }
    if (isSdxl(cfg->family) != manifest.sdxl) {
        error = "Model family does not match pack layout.";
        return false;
    }
    return true;
}

bool ensureNotCancelled(EnclaveDiffusionHandle* handle, std::string& error) {
    if (handle->cancelled.load()) {
        error = "Diffusion generation cancelled.";
        return false;
    }
    return true;
}

void forceUnloadAll(EnclaveDiffusionHandle* handle) {
    handle->clip.unload();
    handle->tokenizer.unload();
    handle->qnn.unloadAll();
}

void applyTrimMemory(EnclaveDiffusionHandle* handle, int level) {
    if (handle == nullptr || level < 0) {
        return;
    }
    if (level >= 10) {
        handle->clip.unload();
        handle->tokenizer.unload();
    }
    if (level >= 15) {
        handle->qnn.unloadAll();
    }
}

void enqueueTrimLevel(EnclaveDiffusionHandle* handle, int level) {
    int previous = handle->pending_trim_level.load();
    while (level > previous &&
           !handle->pending_trim_level.compare_exchange_weak(previous, level)) {
    }
}

void drainPendingTrim(EnclaveDiffusionHandle* handle) {
    const int pending = handle->pending_trim_level.exchange(-1);
    if (pending >= 0) {
        applyTrimMemory(handle, pending);
    }
}

void unloadAfterStage(EnclaveDiffusionHandle* handle, PipelineStage stage, EnclaveMemoryPolicy policy) {
    if (policy != ENCLAVE_MEMORY_POLICY_LOW_RAM) {
        return;
    }
    switch (stage) {
        case PipelineStage::ClipEncode:
            handle->clip.unload();
            handle->tokenizer.unload();
            break;
        case PipelineStage::VaeEncode:
            handle->qnn.unloadVaeEncoder();
            break;
        case PipelineStage::UnetLoop:
            handle->qnn.unloadUnet();
            break;
        case PipelineStage::VaeDecode:
            handle->qnn.unloadVaeDecoder();
            break;
        case PipelineStage::Tokenizer:
            break;
    }
}

bool runPipeline(
    EnclaveDiffusionHandle* handle,
    const EnclaveGenerateParams& params,
    EnclaveProgressFn progress,
    void* user,
    std::string& error) {
    const bool img2img = params.mode == ENCLAVE_DIFFUSION_MODE_IMG2IMG;
    if (params.mode == ENCLAVE_DIFFUSION_MODE_INPAINT) {
        error = "Inpaint is not enabled in this engine build.";
        return false;
    }
    if (img2img && !hasImg2ImgEncoder(handle->manifest)) {
        error = "img2img requires vae_encoder.bin in the model directory.";
        return false;
    }
    if (!isQnn(handle->config.family)) {
        error = "MNN full diffusion is not enabled; CLIP runs through MNN and diffusion graphs run through QNN.";
        return false;
    }

    const int width = params.width > 0 ? params.width : defaultWidth(handle->config.family);
    const int height = params.height > 0 ? params.height : defaultWidth(handle->config.family);
    const int steps = params.steps > 0 ? params.steps : defaultSteps(handle->config.family);
    const float cfg_scale = params.cfg_scale > 0.0f ? params.cfg_scale : defaultCfg(handle->config.family);
    const char* scheduler_name = params.scheduler != nullptr && params.scheduler[0] != '\0'
        ? params.scheduler
        : defaultScheduler(handle->config.family);
    const EnclaveMemoryPolicy policy = params.memory_policy;

    const std::vector<PipelineStage> stages = stagesForMode(img2img, policy == ENCLAVE_MEMORY_POLICY_LOW_RAM);
    StageLoader loader(stages);
    ClipConditioning conditioning;
    VaeMoments vae_moments;
    std::vector<float> latents;
    std::vector<float> noise;
    std::vector<float> rgb;

    std::unique_ptr<Scheduler> scheduler(createScheduler(scheduler_name, handle->manifest.sdxl));
    scheduler->setSeed(params.seed);
    scheduler->setTimesteps(steps);
    const int sample_width = std::max(width / 8, 1);
    const int sample_height = std::max(height / 8, 1);
    const float scale = vaeScale(handle->config.family);
    handle->qnn.setSpatialSize(width, height);
    const LatentShape latent_shape {1, 4, sample_height, sample_width};
    const float denoise_strength =
        params.denoise_strength > 0.0f ? params.denoise_strength : 0.75f;
    int img2img_start_step = 0;
    if (!img2img) {
        noise = generateGaussianLatents(latent_shape, params.seed, scheduler->initNoiseSigma());
    }

    do {
        if (!ensureNotCancelled(handle, error)) {
            return false;
        }
        const PipelineStage stage = loader.current();
        switch (stage) {
            case PipelineStage::Tokenizer:
                if (!handle->tokenizer.isLoaded() &&
                    !handle->tokenizer.load(handle->manifest.tokenizer_json, error)) {
                    return false;
                }
                break;
            case PipelineStage::ClipEncode:
                if (!handle->clip.isLoaded() && !handle->clip.load(handle->manifest, error)) {
                    return false;
                }
                if (!handle->clip.encode(
                        params.prompt != nullptr ? params.prompt : "",
                        params.negative_prompt != nullptr ? params.negative_prompt : "",
                        handle->tokenizer,
                        width,
                        height,
                        conditioning,
                        error)) {
                    return false;
                }
                break;
            case PipelineStage::VaeEncode: {
                Image image;
                if (!readImageRgbFloat(
                        params.init_image_path != nullptr ? params.init_image_path : "",
                        width,
                        height,
                        image,
                        error)) {
                    return false;
                }
                if (!handle->qnn.isVaeEncoderLoaded() &&
                    !handle->qnn.loadVaeEncoder(handle->manifest, handle->qnn_paths, error)) {
                    return false;
                }
                if (!handle->qnn.runVaeEncoder(image.pixels, vae_moments, error)) {
                    return false;
                }
                const size_t latent_count =
                    static_cast<size_t>(latent_shape.batch) * static_cast<size_t>(latent_shape.channels) *
                    static_cast<size_t>(latent_shape.height) * static_cast<size_t>(latent_shape.width);
                if (vae_moments.mean.size() != latent_count || vae_moments.std.size() != latent_count) {
                    error = "VAE encoder output shape does not match latent shape.";
                    return false;
                }
                img2img_start_step = img2imgStartStep(scheduler->numTimesteps(), denoise_strength);
                const int noise_timestep = scheduler->timestepAt(img2img_start_step);
                const float noise_t = static_cast<float>(noise_timestep) / 1000.0f;
                const std::vector<float> vae_sample_noise =
                    generateGaussianLatents(latent_shape, params.seed ^ 0x5641454ELL, 1.0f);
                std::vector<float> z0(latent_count);
                for (size_t i = 0; i < latent_count; ++i) {
                    z0[i] = (vae_moments.mean[i] + vae_moments.std[i] * vae_sample_noise[i]) * scale;
                }
                const std::vector<float> diffusion_noise =
                    generateGaussianLatents(latent_shape, params.seed, 1.0f);
                latents = scheduler->addNoise(z0, diffusion_noise, noise_t);
                break;
            }
            case PipelineStage::UnetLoop:
                if (latents.empty()) {
                    if (noise.empty()) {
                        noise = generateGaussianLatents(
                            latent_shape,
                            params.seed,
                            scheduler->initNoiseSigma());
                    }
                    latents = noise;
                }
                if (!handle->qnn.isUnetLoaded() &&
                    !handle->qnn.loadUnet(handle->manifest, handle->qnn_paths, error)) {
                    return false;
                }
                {
                    const int total_steps = scheduler->numTimesteps() - img2img_start_step;
                    for (int i = img2img_start_step; i < scheduler->numTimesteps(); ++i) {
                        if (!ensureNotCancelled(handle, error)) {
                            return false;
                        }
                        std::vector<float> noise_pred;
                        const std::vector<float> scaled_latents =
                            scheduler->scale_model_input(latents, scheduler->timestepAt(i));
                        if (!handle->qnn.runUnet(
                                scaled_latents,
                                scheduler->timestepAt(i),
                                conditioning,
                                cfg_scale,
                                noise_pred,
                                error)) {
                            return false;
                        }
                        latents = scheduler->step(latents, noise_pred, i);
                        if (progress != nullptr) {
                            progress(i + 1 - img2img_start_step, total_steps, user);
                        }
                    }
                }
                break;
            case PipelineStage::VaeDecode:
                if (!handle->qnn.isVaeDecoderLoaded() &&
                    !handle->qnn.loadVaeDecoder(handle->manifest, handle->qnn_paths, error)) {
                    return false;
                }
                std::vector<float> decode_latents(latents.size());
                for (size_t i = 0; i < latents.size(); ++i) {
                    decode_latents[i] = latents[i] / scale;
                }
                if (!handle->qnn.runVaeDecoder(decode_latents, rgb, error)) {
                    return false;
                }
                break;
        }
        unloadAfterStage(handle, stage, policy);
    } while (loader.advance());

    if (rgb.empty()) {
        error = "Diffusion pipeline completed without decoded pixels.";
        return false;
    }
    if (policy == ENCLAVE_MEMORY_POLICY_LOW_RAM) {
        forceUnloadAll(handle);
    }
    drainPendingTrim(handle);
    return writePngRgbFloat(params.output_png_path != nullptr ? params.output_png_path : "", rgb, width, height, error);
}

}  // namespace
}  // namespace enclave::diffusion

extern "C" EnclaveDiffusionHandle* enclave_diffusion_create(
    const EnclaveDiffusionConfig* cfg,
    char* err,
    int err_len) {
    enclave::diffusion::PackManifest manifest;
    std::string error;
    if (!enclave::diffusion::validateCreateConfig(cfg, manifest, error)) {
        enclave::diffusion::copyError(error, err, err_len);
        return nullptr;
    }

    auto* handle = new EnclaveDiffusionHandle(*cfg);
    handle->manifest = manifest;
    handle->qnn_paths.backend_path = cfg->qnn_backend_path != nullptr ? cfg->qnn_backend_path : "";
    handle->qnn_paths.system_path = cfg->qnn_system_path != nullptr ? cfg->qnn_system_path : "";
    handle->qnn_paths.dsp_library_path = cfg->dsp_library_path != nullptr ? cfg->dsp_library_path : "";
    // SDXL catalog pack (qnn2.28_8gen3) targets Hexagon V79 on 8 Gen 3 / 8s Gen 3 class SoCs.
    handle->qnn_paths.htp_stub_library =
        cfg->family == ENCLAVE_MODEL_SDXL_QNN ? "libQnnHtpV79Stub.so" : "libQnnHtpV73Stub.so";
    return handle;
}

extern "C" void enclave_diffusion_destroy(EnclaveDiffusionHandle* handle) {
    if (handle == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(handle->mutex);
    enclave::diffusion::forceUnloadAll(handle);
    delete handle;
}

extern "C" int enclave_diffusion_generate(
    EnclaveDiffusionHandle* handle,
    const EnclaveGenerateParams* params,
    EnclaveProgressFn progress,
    void* user,
    char* err,
    int err_len) {
    if (handle == nullptr || params == nullptr) {
        enclave::diffusion::copyError("Diffusion handle or params are null.", err, err_len);
        return -1;
    }
    std::lock_guard<std::mutex> lock(handle->mutex);
    handle->cancelled.store(false);
    std::string error;
    const bool ok = enclave::diffusion::runPipeline(handle, *params, progress, user, error);
    if (!ok) {
        enclave::diffusion::drainPendingTrim(handle);
        enclave::diffusion::copyError(error, err, err_len);
        return -1;
    }
    return 0;
}

extern "C" void enclave_diffusion_cancel(EnclaveDiffusionHandle* handle) {
    if (handle != nullptr) {
        handle->cancelled.store(true);
    }
}

extern "C" void enclave_diffusion_on_trim_memory(EnclaveDiffusionHandle* handle, int level) {
    if (handle == nullptr) {
        return;
    }
    // Never block the UI thread (or trim callback thread) on an in-flight ~minute-long generate.
    std::unique_lock<std::mutex> lock(handle->mutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        enclave::diffusion::enqueueTrimLevel(handle, level);
        return;
    }
    enclave::diffusion::applyTrimMemory(handle, level);
    enclave::diffusion::drainPendingTrim(handle);
}
