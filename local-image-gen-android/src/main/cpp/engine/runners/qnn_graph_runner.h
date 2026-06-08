#pragma once

#include "engine/model_layout/pack_manifest.h"
#include "engine/runners/mnn_clip_runner.h"

namespace enclave::diffusion {
class EnclaveQnnApp;
class EnclaveQnnSharedRuntime;
}

#include <memory>
#include <string>
#include <vector>

namespace enclave::diffusion {

struct QnnRuntimePaths {
    std::string backend_path;
    std::string system_path;
    std::string dsp_library_path;
    std::string htp_stub_library;
};

struct VaeMoments {
    std::vector<float> mean;
    std::vector<float> std;
};

class QnnGraphRunner {
 public:
    QnnGraphRunner();
    ~QnnGraphRunner();

    QnnGraphRunner(const QnnGraphRunner&) = delete;
    QnnGraphRunner& operator=(const QnnGraphRunner&) = delete;

    bool loadUnet(const PackManifest& manifest, const QnnRuntimePaths& paths, std::string& error);
    bool loadVaeEncoder(const PackManifest& manifest, const QnnRuntimePaths& paths, std::string& error);
    bool loadVaeDecoder(const PackManifest& manifest, const QnnRuntimePaths& paths, std::string& error);

    void unloadUnet();
    void unloadVaeEncoder();
    void unloadVaeDecoder();
    void unloadAll();

    bool isUnetLoaded() const;
    bool isVaeEncoderLoaded() const;
    bool isVaeDecoderLoaded() const;

    void setSpatialSize(int output_width, int output_height);

    bool runUnet(
        const std::vector<float>& latents,
        int timestep,
        const ClipConditioning& conditioning,
        float cfg_scale,
        std::vector<float>& noise_pred,
        std::string& error);

    bool runVaeEncoder(
        const std::vector<float>& image_chw,
        VaeMoments& moments,
        std::string& error);

    bool runVaeDecoder(
        const std::vector<float>& latents,
        std::vector<float>& image_chw,
        std::string& error);

 private:
    bool ensureSharedRuntime(const QnnRuntimePaths& paths, std::string& error);

    bool loadGraph(
        const std::string& path,
        const QnnRuntimePaths& paths,
        std::unique_ptr<EnclaveQnnApp>& target,
        std::string& error);

    bool sdxl_ = false;
    int output_width_ = 512;
    int output_height_ = 512;
    std::unique_ptr<EnclaveQnnSharedRuntime> shared_runtime_;
    std::unique_ptr<EnclaveQnnApp> unet_;
    std::unique_ptr<EnclaveQnnApp> vae_encoder_;
    std::unique_ptr<EnclaveQnnApp> vae_decoder_;
    std::vector<float> unet_cond_scratch_;
    std::vector<float> unet_uncond_scratch_;
};

}  // namespace enclave::diffusion
