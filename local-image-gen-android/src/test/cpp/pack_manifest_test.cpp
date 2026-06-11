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
    if (!enclave::diffusion::hasRequiredArtifacts(manifest, ENCLAVE_MODEL_SD15_QNN)) {
        std::fprintf(stderr, "expected required SD15 artifacts\n");
        return 1;
    }
    touch("vae_encoder.bin");
    enclave::diffusion::loadPackManifest(dir.string(), manifest);
    if (!enclave::diffusion::hasImg2ImgEncoder(manifest, ENCLAVE_MODEL_SD15_QNN)) {
        std::fprintf(stderr, "expected optional encoder detection\n");
        return 1;
    }

    const fs::path cpu_dir = fs::temp_directory_path() / "enclave_cpu_pack_manifest_test";
    fs::create_directories(cpu_dir);
    const auto touch_cpu = [&](const char* name) {
        std::FILE* f = std::fopen((cpu_dir / name).string().c_str(), "wb");
        if (f != nullptr) {
            std::fclose(f);
        }
    };
    touch_cpu("clip.mnn");
    touch_cpu("unet.mnn");
    touch_cpu("vae_decoder.mnn");
    touch_cpu("tokenizer.json");
    enclave::diffusion::loadPackManifest(cpu_dir.string(), manifest);
    if (!enclave::diffusion::hasRequiredArtifacts(manifest, ENCLAVE_MODEL_SD15_MNN)) {
        std::fprintf(stderr, "expected required MNN CPU artifacts\n");
        return 1;
    }
    if (enclave::diffusion::hasRequiredArtifacts(manifest, ENCLAVE_MODEL_SD15_QNN)) {
        std::fprintf(stderr, "MNN CPU artifacts must not pass QNN validation\n");
        return 1;
    }
    touch_cpu("vae_encoder.mnn");
    enclave::diffusion::loadPackManifest(cpu_dir.string(), manifest);
    if (!enclave::diffusion::hasImg2ImgEncoder(manifest, ENCLAVE_MODEL_SD15_MNN)) {
        std::fprintf(stderr, "expected optional MNN encoder detection\n");
        return 1;
    }

    // xororz / Local Dream CPU layout: clip_v2.mnn + embedding bins instead of clip.mnn.
    const fs::path cpu_v2_dir = fs::temp_directory_path() / "enclave_cpu_v2_pack_manifest_test";
    fs::create_directories(cpu_v2_dir);
    const auto touch_cpu_v2 = [&](const char* name) {
        std::FILE* f = std::fopen((cpu_v2_dir / name).string().c_str(), "wb");
        if (f != nullptr) {
            std::fclose(f);
        }
    };
    touch_cpu_v2("unet.mnn");
    touch_cpu_v2("vae_decoder.mnn");
    touch_cpu_v2("tokenizer.json");
    enclave::diffusion::loadPackManifest(cpu_v2_dir.string(), manifest);
    if (enclave::diffusion::hasRequiredArtifacts(manifest, ENCLAVE_MODEL_SD15_MNN)) {
        std::fprintf(stderr, "MNN CPU pack without any clip variant must not validate\n");
        return 1;
    }
    touch_cpu_v2("clip_v2.mnn");
    touch_cpu_v2("token_emb.bin");
    touch_cpu_v2("pos_emb.bin");
    enclave::diffusion::loadPackManifest(cpu_v2_dir.string(), manifest);
    if (!enclave::diffusion::hasRequiredArtifacts(manifest, ENCLAVE_MODEL_SD15_MNN)) {
        std::fprintf(stderr, "expected clip_v2 embedding layout to validate for MNN CPU\n");
        return 1;
    }
    fs::remove_all(dir);
    fs::remove_all(cpu_dir);
    fs::remove_all(cpu_v2_dir);
    std::printf("pack_manifest_test passed\n");
    return 0;
}
