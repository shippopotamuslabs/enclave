#pragma once

#include "engine/model_layout/pack_manifest.h"
#include "engine/runners/tokenizer_runner.h"

#include <memory>
#include <string>
#include <vector>

namespace enclave::diffusion {

struct ClipConditioning {
    std::vector<float> hidden_states;
    std::vector<float> pooled;
    std::vector<float> time_ids;
    int hidden_size = 768;
    bool sdxl = false;
};

class MnnClipRunner {
 public:
    MnnClipRunner();
    ~MnnClipRunner();

    MnnClipRunner(const MnnClipRunner&) = delete;
    MnnClipRunner& operator=(const MnnClipRunner&) = delete;

    bool load(const PackManifest& manifest, std::string& error);
    void unload();
    bool isLoaded() const;

    bool encode(
        const std::string& prompt,
        const std::string& negative_prompt,
        const TokenizerRunner& tokenizer,
        int output_width,
        int output_height,
        ClipConditioning& conditioning,
        std::string& error);

 private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

}  // namespace enclave::diffusion
