#include "qnn_graph_runner.h"

#include "engine/runners/clip_prompt_utils.h"

#ifdef ENCLAVE_DIFFUSION_HAS_QNN
#include "engine/qnn/enclave_qnn_app.h"
#else
#include "engine/qnn/enclave_qnn_app_stub.h"
#endif

#include <algorithm>
#include <cmath>
#include <fstream>

namespace enclave::diffusion {
namespace {

bool fileExists(const std::string& path) {
    std::ifstream in(path, std::ios::binary);
    return static_cast<bool>(in);
}

std::vector<float> slice(
    const std::vector<float>& values,
    size_t offset,
    size_t count) {
    if (offset + count > values.size()) {
        return {};
    }
    return std::vector<float>(values.begin() + static_cast<long>(offset),
                              values.begin() + static_cast<long>(offset + count));
}

void applyCfg(
    const std::vector<float>& uncond,
    const std::vector<float>& cond,
    float cfg_scale,
    std::vector<float>& out) {
    out.resize(cond.size());
    for (size_t i = 0; i < cond.size(); ++i) {
        const float base = i < uncond.size() ? uncond[i] : 0.0f;
        out[i] = base + cfg_scale * (cond[i] - base);
    }
}

}  // namespace

QnnGraphRunner::QnnGraphRunner() = default;

QnnGraphRunner::~QnnGraphRunner() { unloadAll(); }

bool QnnGraphRunner::loadGraph(
    const std::string& path,
    const QnnRuntimePaths& paths,
    std::unique_ptr<EnclaveQnnApp>& target,
    std::string& error) {
#ifndef ENCLAVE_DIFFUSION_HAS_QNN
    (void)path;
    (void)paths;
    (void)target;
    error = "QNN graph execution is not linked into this build.";
    return false;
#else
    if (!fileExists(path)) {
        error = "Missing QNN graph binary: " + path;
        return false;
    }
    auto app = std::make_unique<EnclaveQnnApp>();
    if (!app->initializeFromBinary(
            path,
            paths.backend_path,
            paths.system_path,
            paths.dsp_library_path,
            paths.htp_stub_library,
            error)) {
        return false;
    }
    target = std::move(app);
    return true;
#endif
}

void QnnGraphRunner::setSpatialSize(int output_width, int output_height) {
    output_width_ = output_width;
    output_height_ = output_height;
#ifdef ENCLAVE_DIFFUSION_HAS_QNN
    if (unet_) {
        unet_->setSpatialSize(output_width, output_height);
    }
    if (vae_encoder_) {
        vae_encoder_->setSpatialSize(output_width, output_height);
    }
    if (vae_decoder_) {
        vae_decoder_->setSpatialSize(output_width, output_height);
    }
#endif
}

bool QnnGraphRunner::loadUnet(
    const PackManifest& manifest,
    const QnnRuntimePaths& paths,
    std::string& error) {
    sdxl_ = manifest.sdxl;
    if (!loadGraph(manifest.unet_bin, paths, unet_, error)) {
        return false;
    }
    unet_->setSpatialSize(output_width_, output_height_);
    return true;
}

bool QnnGraphRunner::loadVaeEncoder(
    const PackManifest& manifest,
    const QnnRuntimePaths& paths,
    std::string& error) {
    sdxl_ = manifest.sdxl;
    if (!loadGraph(manifest.vae_encoder_bin, paths, vae_encoder_, error)) {
        return false;
    }
    vae_encoder_->setSpatialSize(output_width_, output_height_);
    return true;
}

bool QnnGraphRunner::loadVaeDecoder(
    const PackManifest& manifest,
    const QnnRuntimePaths& paths,
    std::string& error) {
    sdxl_ = manifest.sdxl;
    if (!loadGraph(manifest.vae_decoder_bin, paths, vae_decoder_, error)) {
        return false;
    }
    vae_decoder_->setSpatialSize(output_width_, output_height_);
    return true;
}

void QnnGraphRunner::unloadUnet() { unet_.reset(); }

void QnnGraphRunner::unloadVaeEncoder() { vae_encoder_.reset(); }

void QnnGraphRunner::unloadVaeDecoder() { vae_decoder_.reset(); }

void QnnGraphRunner::unloadAll() {
    unloadUnet();
    unloadVaeEncoder();
    unloadVaeDecoder();
}

bool QnnGraphRunner::isUnetLoaded() const { return unet_ != nullptr; }

bool QnnGraphRunner::isVaeEncoderLoaded() const { return vae_encoder_ != nullptr; }

bool QnnGraphRunner::isVaeDecoderLoaded() const { return vae_decoder_ != nullptr; }

bool QnnGraphRunner::runUnet(
    const std::vector<float>& latents,
    int timestep,
    const ClipConditioning& conditioning,
    float cfg_scale,
    std::vector<float>& noise_pred,
    std::string& error) {
#ifndef ENCLAVE_DIFFUSION_HAS_QNN
    (void)latents;
    (void)timestep;
    (void)conditioning;
    (void)cfg_scale;
    (void)noise_pred;
    error = "QNN graph execution is not linked into this build.";
    return false;
#else
    if (!unet_) {
        error = "UNet graph is not loaded.";
        return false;
    }
    const size_t hidden_count = static_cast<size_t>(kClipTokenCount) *
                                static_cast<size_t>(conditioning.hidden_size);
    const std::vector<float> uncond_hidden = slice(conditioning.hidden_states, 0, hidden_count);
    const std::vector<float> cond_hidden = slice(conditioning.hidden_states, hidden_count, hidden_count);
    if (cond_hidden.empty()) {
        error = "CLIP conditioning is missing positive hidden states.";
        return false;
    }

    std::vector<float> cond;
    if (sdxl_) {
        const std::vector<float> cond_pool = slice(conditioning.pooled, 1280, 1280);
        if (cond_pool.empty()) {
            error = "SDXL conditioning is missing positive pooled embedding.";
            return false;
        }
        if (!unet_->executeSdxlUnet(
                latents,
                cond_hidden,
                timestep,
                conditioning.time_ids,
                cond_pool,
                cond,
                error)) {
            return false;
        }
    } else if (!unet_->executeSd15Unet(latents, timestep, cond_hidden, cond, error)) {
        return false;
    }

    if (std::fabs(cfg_scale - 1.0f) < 0.0001f || uncond_hidden.empty()) {
        noise_pred = std::move(cond);
        return true;
    }

    std::vector<float> uncond;
    if (sdxl_) {
        const std::vector<float> uncond_pool = slice(conditioning.pooled, 0, 1280);
        if (uncond_pool.empty()) {
            error = "SDXL conditioning is missing negative pooled embedding.";
            return false;
        }
        if (!unet_->executeSdxlUnet(
                latents,
                uncond_hidden,
                timestep,
                conditioning.time_ids,
                uncond_pool,
                uncond,
                error)) {
            return false;
        }
    } else if (!unet_->executeSd15Unet(latents, timestep, uncond_hidden, uncond, error)) {
        return false;
    }
    applyCfg(uncond, cond, cfg_scale, noise_pred);
    return true;
#endif
}

bool QnnGraphRunner::runVaeEncoder(
    const std::vector<float>& image_chw,
    VaeMoments& moments,
    std::string& error) {
#ifndef ENCLAVE_DIFFUSION_HAS_QNN
    (void)image_chw;
    (void)moments;
    error = "QNN graph execution is not linked into this build.";
    return false;
#else
    if (!vae_encoder_) {
        error = "VAE encoder graph is not loaded.";
        return false;
    }
    return sdxl_
        ? vae_encoder_->executeSdxlVaeEncoder(image_chw, moments.mean, moments.std, error)
        : vae_encoder_->executeSd15VaeEncoder(image_chw, moments.mean, moments.std, error);
#endif
}

bool QnnGraphRunner::runVaeDecoder(
    const std::vector<float>& latents,
    std::vector<float>& image_chw,
    std::string& error) {
#ifndef ENCLAVE_DIFFUSION_HAS_QNN
    (void)latents;
    (void)image_chw;
    error = "QNN graph execution is not linked into this build.";
    return false;
#else
    if (!vae_decoder_) {
        error = "VAE decoder graph is not loaded.";
        return false;
    }
    return sdxl_
        ? vae_decoder_->executeSdxlVaeDecoder(latents, image_chw, error)
        : vae_decoder_->executeSd15VaeDecoder(latents, image_chw, error);
#endif
}

}  // namespace enclave::diffusion
