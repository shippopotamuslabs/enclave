#include "engine/model_layout/pack_manifest.h"

#include <cstdio>
#include <filesystem>

namespace fs = std::filesystem;

int main() {
    const fs::path dir = fs::temp_directory_path() / "enclave_pack_manifest_test";
    fs::create_directories(dir);
    const auto touch = [&](const char* name) {
        std::FILE* f = std::fopen((dir / name).string().c_str(), "wb");
        if (f != nullptr) {
            std::fclose(f);
        }
    };
    touch("clip_v2.mnn");
    touch("token_emb.bin");
    touch("pos_emb.bin");
    touch("unet.bin");
    touch("vae_decoder.bin");
    touch("tokenizer.json");

    enclave::diffusion::PackManifest manifest;
    enclave::diffusion::loadPackManifest(dir.string(), manifest);
    if (!enclave::diffusion::hasRequiredArtifacts(manifest)) {
        std::fprintf(stderr, "expected required SD15 artifacts\n");
        return 1;
    }
    touch("vae_encoder.bin");
    enclave::diffusion::loadPackManifest(dir.string(), manifest);
    if (!enclave::diffusion::hasImg2ImgEncoder(manifest)) {
        std::fprintf(stderr, "expected optional encoder detection\n");
        return 1;
    }
    fs::remove_all(dir);
    std::printf("pack_manifest_test passed\n");
    return 0;
}
