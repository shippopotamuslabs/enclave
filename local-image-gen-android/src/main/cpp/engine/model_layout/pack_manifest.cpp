#include "pack_manifest.h"

#include <sys/stat.h>

#include <utility>

namespace enclave::diffusion {
namespace {

bool isFile(const std::string& path) {
    struct stat st {};
    return stat(path.c_str(), &st) == 0 && S_ISREG(st.st_mode);
}

std::string join(const std::string& dir, const char* name) {
    if (dir.empty()) {
        return name;
    }
    if (dir.back() == '/') {
        return dir + name;
    }
    return dir + "/" + name;
}

}  // namespace

bool loadPackManifest(const std::string& model_dir, PackManifest& out) {
    out = PackManifest{};
    out.sdxl = isFile(join(model_dir, "clip_2.mnn"));
    out.unet_bin = join(model_dir, "unet.bin");
    out.vae_decoder_bin = join(model_dir, "vae_decoder.bin");
    out.vae_encoder_bin = join(model_dir, "vae_encoder.bin");
    out.unet_mnn = join(model_dir, "unet.mnn");
    out.vae_decoder_mnn = join(model_dir, "vae_decoder.mnn");
    out.vae_encoder_mnn = join(model_dir, "vae_encoder.mnn");
    out.tokenizer_json = join(model_dir, "tokenizer.json");
    out.clip_v2_mnn = join(model_dir, "clip_v2.mnn");
    out.clip_mnn = join(model_dir, "clip.mnn");
    out.clip_2_mnn = join(model_dir, "clip_2.mnn");
    out.token_emb_bin = join(model_dir, "token_emb.bin");
    out.token_emb_2_bin = join(model_dir, "token_emb_2.bin");
    out.pos_emb_bin = join(model_dir, "pos_emb.bin");
    out.pos_emb_2_bin = join(model_dir, "pos_emb_2.bin");
    return !model_dir.empty();
}

bool hasRequiredArtifacts(const PackManifest& manifest, EnclaveModelFamily family) {
    if (family == ENCLAVE_MODEL_SD15_MNN) {
        // CPU packs ship either clip.mnn (built-in embeddings) or clip_v2.mnn plus
        // external token/pos embedding bins (xororz / Local Dream layout).
        const bool has_clip =
            isFile(manifest.clip_mnn) ||
            (isFile(manifest.clip_v2_mnn) && isFile(manifest.token_emb_bin) &&
             isFile(manifest.pos_emb_bin));
        return has_clip && isFile(manifest.unet_mnn) && isFile(manifest.vae_decoder_mnn) &&
               isFile(manifest.tokenizer_json);
    }
    if (!isFile(manifest.unet_bin) || !isFile(manifest.vae_decoder_bin) ||
        !isFile(manifest.tokenizer_json)) {
        return false;
    }
    if (manifest.sdxl) {
        return isFile(manifest.clip_mnn) && isFile(manifest.clip_2_mnn) &&
               isFile(manifest.token_emb_bin) && isFile(manifest.token_emb_2_bin) &&
               isFile(manifest.pos_emb_bin) && isFile(manifest.pos_emb_2_bin);
    }
    return isFile(manifest.clip_v2_mnn) && isFile(manifest.token_emb_bin) &&
           isFile(manifest.pos_emb_bin);
}

bool hasImg2ImgEncoder(const PackManifest& manifest, EnclaveModelFamily family) {
    return isFile(
        family == ENCLAVE_MODEL_SD15_MNN ? manifest.vae_encoder_mnn : manifest.vae_encoder_bin);
}

}  // namespace enclave::diffusion
