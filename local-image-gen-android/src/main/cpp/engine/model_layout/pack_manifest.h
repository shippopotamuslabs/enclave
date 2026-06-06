#pragma once

#include <string>
#include <vector>

namespace enclave::diffusion {

struct PackManifest {
    bool sdxl = false;
    std::string unet_bin;
    std::string vae_decoder_bin;
    std::string vae_encoder_bin;
    std::string tokenizer_json;
    std::string clip_v2_mnn;
    std::string clip_mnn;
    std::string clip_2_mnn;
    std::string token_emb_bin;
    std::string token_emb_2_bin;
    std::string pos_emb_bin;
    std::string pos_emb_2_bin;
};

bool loadPackManifest(const std::string& model_dir, PackManifest& out);

bool hasRequiredArtifacts(const PackManifest& manifest);

bool hasImg2ImgEncoder(const PackManifest& manifest);

}  // namespace enclave::diffusion
