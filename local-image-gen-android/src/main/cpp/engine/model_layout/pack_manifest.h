#pragma once

#include "engine/include/enclave_diffusion.h"

#include <string>
#include <vector>

namespace enclave::diffusion {

struct PackManifest {
    bool sdxl = false;
    std::string unet_bin;
    std::string vae_decoder_bin;
    std::string vae_encoder_bin;
    std::string unet_mnn;
    std::string vae_decoder_mnn;
    std::string vae_encoder_mnn;
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

bool hasRequiredArtifacts(const PackManifest& manifest, EnclaveModelFamily family);

bool hasImg2ImgEncoder(const PackManifest& manifest, EnclaveModelFamily family);

}  // namespace enclave::diffusion
