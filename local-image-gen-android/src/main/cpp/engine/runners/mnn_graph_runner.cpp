#include "mnn_graph_runner.h"

#include "engine/runners/clip_prompt_utils.h"

#include <algorithm>
#include <cmath>
#include <fstream>

#ifdef ENCLAVE_DIFFUSION_HAS_MNN
#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>
#endif

namespace enclave::diffusion {
namespace {

#ifdef ENCLAVE_DIFFUSION_HAS_MNN
struct MnnModelSession {
    std::shared_ptr<MNN::Interpreter> interpreter;
    MNN::Session* session = nullptr;
};

bool createSession(const std::string& path, MnnModelSession& target, std::string& error) {
    std::ifstream file(path, std::ios::binary);
    if (!file) {
        error = "Missing MNN graph: " + path;
        return false;
    }
    target.interpreter.reset(MNN::Interpreter::createFromFile(path.c_str()));
    if (!target.interpreter) {
        error = "Failed to create MNN interpreter for " + path;
        return false;
    }
    MNN::ScheduleConfig config;
    config.type = MNN_FORWARD_CPU;
    config.numThread = 4;
    MNN::BackendConfig backend_config;
    backend_config.memory = MNN::BackendConfig::Memory_Low;
    backend_config.power = MNN::BackendConfig::Power_High;
    config.backendConfig = &backend_config;
    target.session = target.interpreter->createSession(config);
    if (target.session == nullptr) {
        error = "Failed to create MNN session for " + path;
        target.interpreter.reset();
        return false;
    }
    return true;
}

void releaseSession(MnnModelSession& model) {
    if (model.interpreter != nullptr && model.session != nullptr) {
        model.interpreter->releaseSession(model.session);
    }
    model.session = nullptr;
    model.interpreter.reset();
}

bool resizeInput(
    MnnModelSession& model,
    const char* name,
    const std::vector<int>& shape,
    std::string& error) {
    MNN::Tensor* input = model.interpreter->getSessionInput(model.session, name);
    if (input == nullptr) {
        error = std::string("MNN input tensor not found: ") + name;
        return false;
    }
    model.interpreter->resizeTensor(input, shape);
    return true;
}

bool copyFloatInput(
    MnnModelSession& model,
    const char* name,
    const std::vector<float>& values,
    std::string& error) {
    MNN::Tensor* input = model.interpreter->getSessionInput(model.session, name);
    if (input == nullptr) {
        error = std::string("MNN input tensor not found: ") + name;
        return false;
    }
    MNN::Tensor host(input, MNN::Tensor::CAFFE);
    if (static_cast<size_t>(host.elementSize()) != values.size()) {
        error = std::string("MNN input tensor size mismatch: ") + name;
        return false;
    }
    std::copy(values.begin(), values.end(), host.host<float>());
    input->copyFromHostTensor(&host);
    return true;
}

bool copyIntInput(
    MnnModelSession& model,
    const char* name,
    int value,
    std::string& error) {
    MNN::Tensor* input = model.interpreter->getSessionInput(model.session, name);
    if (input == nullptr) {
        error = std::string("MNN input tensor not found: ") + name;
        return false;
    }
    MNN::Tensor host(input, MNN::Tensor::CAFFE);
    host.host<int>()[0] = value;
    input->copyFromHostTensor(&host);
    return true;
}

bool readFloatOutput(
    MnnModelSession& model,
    const char* name,
    std::vector<float>& values,
    std::string& error) {
    MNN::Tensor* output = model.interpreter->getSessionOutput(model.session, name);
    if (output == nullptr) {
        error = std::string("MNN output tensor not found: ") + name;
        return false;
    }
    MNN::Tensor host(output, MNN::Tensor::CAFFE);
    output->copyToHostTensor(&host);
    values.assign(host.host<float>(), host.host<float>() + host.elementSize());
    return true;
}

bool run(MnnModelSession& model, std::string& error) {
    if (model.interpreter->runSession(model.session) != MNN::NO_ERROR) {
        error = "MNN session execution failed.";
        return false;
    }
    return true;
}
#endif

void applyCfg(
    const std::vector<float>& batch,
    size_t latent_count,
    float cfg_scale,
    std::vector<float>& out) {
    out.resize(latent_count);
    for (size_t i = 0; i < latent_count; ++i) {
        const float uncond = batch[i];
        const float cond = batch[latent_count + i];
        out[i] = uncond + cfg_scale * (cond - uncond);
    }
}

}  // namespace

struct MnnGraphRunner::Impl {
    int output_width = 512;
    int output_height = 512;
#ifdef ENCLAVE_DIFFUSION_HAS_MNN
    MnnModelSession unet;
    MnnModelSession vae_encoder;
    MnnModelSession vae_decoder;
#endif
};

MnnGraphRunner::MnnGraphRunner() : impl_(std::make_unique<Impl>()) {}

MnnGraphRunner::~MnnGraphRunner() { unloadAll(); }

bool MnnGraphRunner::loadUnet(const PackManifest& manifest, std::string& error) {
#ifdef ENCLAVE_DIFFUSION_HAS_MNN
    unloadUnet();
    return createSession(manifest.unet_mnn, impl_->unet, error);
#else
    (void)manifest;
    error = "MNN graph execution is not linked into this build.";
    return false;
#endif
}

bool MnnGraphRunner::loadVaeEncoder(const PackManifest& manifest, std::string& error) {
#ifdef ENCLAVE_DIFFUSION_HAS_MNN
    unloadVaeEncoder();
    return createSession(manifest.vae_encoder_mnn, impl_->vae_encoder, error);
#else
    (void)manifest;
    error = "MNN graph execution is not linked into this build.";
    return false;
#endif
}

bool MnnGraphRunner::loadVaeDecoder(const PackManifest& manifest, std::string& error) {
#ifdef ENCLAVE_DIFFUSION_HAS_MNN
    unloadVaeDecoder();
    return createSession(manifest.vae_decoder_mnn, impl_->vae_decoder, error);
#else
    (void)manifest;
    error = "MNN graph execution is not linked into this build.";
    return false;
#endif
}

void MnnGraphRunner::unloadUnet() {
#ifdef ENCLAVE_DIFFUSION_HAS_MNN
    releaseSession(impl_->unet);
#endif
}

void MnnGraphRunner::unloadVaeEncoder() {
#ifdef ENCLAVE_DIFFUSION_HAS_MNN
    releaseSession(impl_->vae_encoder);
#endif
}

void MnnGraphRunner::unloadVaeDecoder() {
#ifdef ENCLAVE_DIFFUSION_HAS_MNN
    releaseSession(impl_->vae_decoder);
#endif
}

void MnnGraphRunner::unloadAll() {
    unloadUnet();
    unloadVaeEncoder();
    unloadVaeDecoder();
}

bool MnnGraphRunner::isUnetLoaded() const {
#ifdef ENCLAVE_DIFFUSION_HAS_MNN
    return impl_->unet.interpreter != nullptr && impl_->unet.session != nullptr;
#else
    return false;
#endif
}

bool MnnGraphRunner::isVaeEncoderLoaded() const {
#ifdef ENCLAVE_DIFFUSION_HAS_MNN
    return impl_->vae_encoder.interpreter != nullptr && impl_->vae_encoder.session != nullptr;
#else
    return false;
#endif
}

bool MnnGraphRunner::isVaeDecoderLoaded() const {
#ifdef ENCLAVE_DIFFUSION_HAS_MNN
    return impl_->vae_decoder.interpreter != nullptr && impl_->vae_decoder.session != nullptr;
#else
    return false;
#endif
}

void MnnGraphRunner::setSpatialSize(int output_width, int output_height) {
    impl_->output_width = std::max(output_width, 8);
    impl_->output_height = std::max(output_height, 8);
}

bool MnnGraphRunner::runUnet(
    const std::vector<float>& latents,
    int timestep,
    const ClipConditioning& conditioning,
    float cfg_scale,
    std::vector<float>& noise_pred,
    std::string& error) {
#ifndef ENCLAVE_DIFFUSION_HAS_MNN
    (void)latents;
    (void)timestep;
    (void)conditioning;
    (void)cfg_scale;
    (void)noise_pred;
    error = "MNN graph execution is not linked into this build.";
    return false;
#else
    if (!isUnetLoaded()) {
        error = "MNN UNet graph is not loaded.";
        return false;
    }
    const int sample_width = std::max(impl_->output_width / 8, 1);
    const int sample_height = std::max(impl_->output_height / 8, 1);
    const size_t latent_count = static_cast<size_t>(4 * sample_width * sample_height);
    const size_t hidden_count = static_cast<size_t>(2 * kClipTokenCount * conditioning.hidden_size);
    if (latents.size() != latent_count || conditioning.hidden_states.size() != hidden_count) {
        error = "MNN UNet input sizes do not match the SD1.5 graph.";
        return false;
    }
    std::vector<float> latent_batch;
    latent_batch.reserve(latent_count * 2);
    latent_batch.insert(latent_batch.end(), latents.begin(), latents.end());
    latent_batch.insert(latent_batch.end(), latents.begin(), latents.end());
    if (!resizeInput(
            impl_->unet,
            "sample",
            {2, 4, sample_height, sample_width},
            error) ||
        !resizeInput(impl_->unet, "timestep", {1}, error) ||
        !resizeInput(
            impl_->unet,
            "encoder_hidden_states",
            {2, kClipTokenCount, conditioning.hidden_size},
            error)) {
        return false;
    }
    impl_->unet.interpreter->resizeSession(impl_->unet.session);
    if (!copyFloatInput(impl_->unet, "sample", latent_batch, error) ||
        !copyIntInput(impl_->unet, "timestep", timestep, error) ||
        !copyFloatInput(impl_->unet, "encoder_hidden_states", conditioning.hidden_states, error) ||
        !run(impl_->unet, error)) {
        return false;
    }
    std::vector<float> batch;
    if (!readFloatOutput(impl_->unet, "out_sample", batch, error) ||
        batch.size() != latent_count * 2) {
        error = "MNN UNet output size does not match the SD1.5 graph.";
        return false;
    }
    applyCfg(batch, latent_count, cfg_scale, noise_pred);
    return true;
#endif
}

bool MnnGraphRunner::runVaeEncoder(
    const std::vector<float>& image_chw,
    VaeMoments& moments,
    std::string& error) {
#ifndef ENCLAVE_DIFFUSION_HAS_MNN
    (void)image_chw;
    (void)moments;
    error = "MNN graph execution is not linked into this build.";
    return false;
#else
    if (!isVaeEncoderLoaded()) {
        error = "MNN VAE encoder graph is not loaded.";
        return false;
    }
    if (!resizeInput(
            impl_->vae_encoder,
            "input",
            {1, 3, impl_->output_height, impl_->output_width},
            error)) {
        return false;
    }
    impl_->vae_encoder.interpreter->resizeSession(impl_->vae_encoder.session);
    if (!copyFloatInput(impl_->vae_encoder, "input", image_chw, error) ||
        !run(impl_->vae_encoder, error)) {
        return false;
    }
    return readFloatOutput(impl_->vae_encoder, "mean", moments.mean, error) &&
           readFloatOutput(impl_->vae_encoder, "std", moments.std, error);
#endif
}

bool MnnGraphRunner::runVaeDecoder(
    const std::vector<float>& latents,
    std::vector<float>& image_chw,
    std::string& error) {
#ifndef ENCLAVE_DIFFUSION_HAS_MNN
    (void)latents;
    (void)image_chw;
    error = "MNN graph execution is not linked into this build.";
    return false;
#else
    if (!isVaeDecoderLoaded()) {
        error = "MNN VAE decoder graph is not loaded.";
        return false;
    }
    const int sample_width = std::max(impl_->output_width / 8, 1);
    const int sample_height = std::max(impl_->output_height / 8, 1);
    if (!resizeInput(
            impl_->vae_decoder,
            "latent_sample",
            {1, 4, sample_height, sample_width},
            error)) {
        return false;
    }
    impl_->vae_decoder.interpreter->resizeSession(impl_->vae_decoder.session);
    if (!copyFloatInput(impl_->vae_decoder, "latent_sample", latents, error) ||
        !run(impl_->vae_decoder, error)) {
        return false;
    }
    return readFloatOutput(impl_->vae_decoder, "sample", image_chw, error);
#endif
}

}  // namespace enclave::diffusion
