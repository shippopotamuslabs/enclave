#pragma once

#include "engine/model_layout/pack_manifest.h"
#include "engine/runners/mnn_clip_runner.h"
#include "engine/runners/qnn_graph_runner.h"

#include <memory>
#include <string>
#include <vector>

namespace enclave::diffusion {

class MnnGraphRunner {
 public:
    MnnGraphRunner();
    ~MnnGraphRunner();

    MnnGraphRunner(const MnnGraphRunner&) = delete;
    MnnGraphRunner& operator=(const MnnGraphRunner&) = delete;

    bool loadUnet(const PackManifest& manifest, std::string& error);
    bool loadVaeEncoder(const PackManifest& manifest, std::string& error);
    bool loadVaeDecoder(const PackManifest& manifest, std::string& error);

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
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

}  // namespace enclave::diffusion
