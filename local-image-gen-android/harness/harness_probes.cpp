#include "harness_probes.h"

#include "engine/model_layout/pack_manifest.h"
#include "engine/qnn/qnn_graph_info.h"
#include "engine/qnn/qnn_runtime.h"
#include "engine/qnn/qnn_tensor_access.h"
#include "engine/runners/mnn_clip_runner.h"
#include "engine/runners/qnn_graph_runner.h"
#include "engine/runners/tokenizer_runner.h"

#include <cstdio>
#include <fstream>
#include <iostream>
#include <sys/stat.h>
#include <vector>

namespace enclave::harness {
namespace {

constexpr int kErrBuf = 4096;

int failStep(const char* step, const std::string& error) {
    std::cerr << "[FAIL] " << step << ": " << error << '\n';
    return 1;
}

int passStep(const char* step, const std::string& detail = "") {
    std::cout << "[PASS] " << step;
    if (!detail.empty()) {
        std::cout << " — " << detail;
    }
    std::cout << '\n';
    return 0;
}

bool readBinaryFile(const std::string& path, std::vector<uint8_t>& buffer, std::string& error) {
    std::ifstream input(path, std::ios::binary | std::ios::ate);
    if (!input) {
        error = "Failed to open binary: " + path;
        return false;
    }
    const auto size = input.tellg();
    if (size <= 0) {
        error = "Binary is empty: " + path;
        return false;
    }
    buffer.resize(static_cast<size_t>(size));
    input.seekg(0, std::ios::beg);
    if (!input.read(reinterpret_cast<char*>(buffer.data()), size)) {
        error = "Failed to read binary: " + path;
        return false;
    }
    return true;
}

const char* tensorName(const Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.name;
    }
    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return tensor.v2.name;
    }
    return nullptr;
}

void printTensorSummary(const char* graph_name, const Qnn_Tensor_t* tensors, uint32_t count) {
    using namespace enclave::diffusion::qnn_tensor_access;
    std::cout << "  graph=" << graph_name << " tensors=" << count << '\n';
    for (uint32_t i = 0; i < count; ++i) {
        const auto& tensor = tensors[i];
        const char* name = tensorName(tensor);
        std::cout << "    [" << i << "] v" << tensor.version << " name=" << (name != nullptr ? name : "?")
                  << " rank=" << rank(tensor) << " dims=";
        const uint32_t tensor_rank = rank(tensor);
        const uint32_t* dims = dimensions(tensor);
        if (dims == nullptr || tensor_rank == 0) {
            std::cout << "[]";
        } else {
            std::cout << '[';
            for (uint32_t d = 0; d < tensor_rank; ++d) {
                if (d > 0) {
                    std::cout << ',';
                }
                std::cout << dims[d];
            }
            std::cout << ']';
        }
        std::cout << '\n';
    }
}

int probeBinaryMetadata(
    const std::string& label,
    const std::string& binary_path,
    const std::string& system_path) {
    enclave::diffusion::EnclaveQnnApis apis {};
    void* system_handle = nullptr;
    std::string error;
    if (!enclave::diffusion::acquireEnclaveQnnSystem(system_path, apis, &system_handle, error)) {
        return failStep(label.c_str(), error);
    }

    std::vector<uint8_t> buffer;
    if (!readBinaryFile(binary_path, buffer, error)) {
        enclave::diffusion::releaseEnclaveQnnSystem(system_handle);
        return failStep(label.c_str(), error);
    }

    if (apis.system.systemContextCreate == nullptr ||
        apis.system.systemContextGetBinaryInfo == nullptr ||
        apis.system.systemContextFree == nullptr) {
        enclave::diffusion::releaseEnclaveQnnSystem(system_handle);
        return failStep(label.c_str(), "QNN system interface is incomplete.");
    }

    QnnSystemContext_Handle_t system_context = nullptr;
    if (apis.system.systemContextCreate(&system_context) != QNN_SUCCESS) {
        enclave::diffusion::releaseEnclaveQnnSystem(system_handle);
        return failStep(label.c_str(), "systemContextCreate failed.");
    }

    const QnnSystemContext_BinaryInfo_t* binary_info = nullptr;
    Qnn_ContextBinarySize_t binary_info_size = 0;
    if (apis.system.systemContextGetBinaryInfo(
            system_context,
            buffer.data(),
            buffer.size(),
            &binary_info,
            &binary_info_size) != QNN_SUCCESS) {
        apis.system.systemContextFree(system_context);
        enclave::diffusion::releaseEnclaveQnnSystem(system_handle);
        return failStep(label.c_str(), "systemContextGetBinaryInfo failed.");
    }

    enclave::diffusion::EnclaveGraphInfoPtr* graphs_info = nullptr;
    uint32_t graphs_count = 0;
    if (!enclave::diffusion::copyBinaryMetadataToGraphs(binary_info, &graphs_info, graphs_count)) {
        apis.system.systemContextFree(system_context);
        enclave::diffusion::releaseEnclaveQnnSystem(system_handle);
        return failStep(label.c_str(), "copyBinaryMetadataToGraphs failed (tensor version?).");
    }

    std::cout << label << " graphs=" << graphs_count << '\n';
    for (uint32_t i = 0; i < graphs_count; ++i) {
        const auto* graph = graphs_info[i];
        printTensorSummary(
            graph->graph_name != nullptr ? graph->graph_name : "unknown",
            graph->input_tensors,
            graph->num_input_tensors);
        printTensorSummary(
            graph->graph_name != nullptr ? graph->graph_name : "unknown",
            graph->output_tensors,
            graph->num_output_tensors);
    }

    enclave::diffusion::freeEnclaveGraphsInfo(&graphs_info, graphs_count);
    apis.system.systemContextFree(system_context);
    enclave::diffusion::releaseEnclaveQnnSystem(system_handle);
    return passStep(label.c_str(), binary_path);
}

}  // namespace

int runValidate(const HarnessOptions& options) {
    enclave::diffusion::PackManifest manifest;
    if (!enclave::diffusion::loadPackManifest(options.model_dir, manifest)) {
        return failStep("validate", "loadPackManifest failed.");
    }
    if (!enclave::diffusion::hasRequiredArtifacts(manifest)) {
        return failStep("validate", "Required artifacts are missing.");
    }
    const std::string detail = manifest.sdxl ? "sdxl pack" : "sd15 pack";
    return passStep("validate", detail);
}

int runQnnMetadata(const HarnessOptions& options) {
    enclave::diffusion::PackManifest manifest;
    std::string error;
    if (!enclave::diffusion::loadPackManifest(options.model_dir, manifest)) {
        return failStep("qnn-metadata", "loadPackManifest failed.");
    }
    const auto qnn_paths = buildQnnPaths(options);
    struct stat system_stat {};
    if (stat(qnn_paths.system_path.c_str(), &system_stat) != 0) {
        return failStep("qnn-metadata", "Missing system library: " + qnn_paths.system_path);
    }

    int code = 0;
    code |= probeBinaryMetadata("qnn-metadata/unet", manifest.unet_bin, qnn_paths.system_path);
    code |= probeBinaryMetadata("qnn-metadata/vae-decoder", manifest.vae_decoder_bin, qnn_paths.system_path);
    if (!manifest.vae_encoder_bin.empty()) {
        code |= probeBinaryMetadata("qnn-metadata/vae-encoder", manifest.vae_encoder_bin, qnn_paths.system_path);
    }
    return code == 0 ? 0 : 1;
}

int runClipProbe(const HarnessOptions& options) {
    enclave::diffusion::PackManifest manifest;
    std::string error;
    if (!enclave::diffusion::loadPackManifest(options.model_dir, manifest)) {
        return failStep("clip", "loadPackManifest failed.");
    }

    enclave::diffusion::TokenizerRunner tokenizer;
    if (!tokenizer.load(manifest.tokenizer_json, error)) {
        return failStep("clip", error);
    }

    enclave::diffusion::MnnClipRunner clip;
    if (!clip.load(manifest, error)) {
        return failStep("clip", error);
    }

    const int width = options.width > 0 ? options.width : (manifest.sdxl ? 1024 : 512);
    const int height = options.height > 0 ? options.height : width;
    enclave::diffusion::ClipConditioning conditioning;
    if (!clip.encode(options.prompt, options.negative_prompt, tokenizer, width, height, conditioning, error)) {
        return failStep("clip", error);
    }

    const std::string detail = "hidden=" + std::to_string(conditioning.hidden_states.size()) +
        " pooled=" + std::to_string(conditioning.pooled.size());
    return passStep("clip", detail);
}

int runQnnInit(const HarnessOptions& options) {
#ifndef __ANDROID__
    return failStep(
        "qnn-init",
        "HTP context init requires the Android device CLI. Use scripts/run-enclave-diffusion-harness.sh --target device.");
#else
    enclave::diffusion::PackManifest manifest;
    std::string error;
    if (!enclave::diffusion::loadPackManifest(options.model_dir, manifest)) {
        return failStep("qnn-init", "loadPackManifest failed.");
    }

    const auto qnn_paths = buildQnnPaths(options);
    enclave::diffusion::QnnGraphRunner qnn;
    const int width = options.width > 0 ? options.width : (manifest.sdxl ? 1024 : 512);
    const int height = options.height > 0 ? options.height : width;
    qnn.setSpatialSize(width, height);
    if (!qnn.loadUnet(manifest, qnn_paths, error)) {
        return failStep("qnn-init", error);
    }
    return passStep("qnn-init", manifest.unet_bin);
#endif
}

int runGenerate(HarnessOptions& options) {
#ifndef __ANDROID__
    return failStep(
        "generate",
        "Full HTP generation requires the Android device CLI. Use scripts/run-enclave-diffusion-harness.sh --target device.");
#else
    char err[kErrBuf] {};
    const auto cfg = buildDiffusionConfig(options);
    EnclaveDiffusionHandle* handle = enclave_diffusion_create(&cfg, err, kErrBuf);
    if (handle == nullptr) {
        return failStep("generate", err);
    }

    EnclaveGenerateParams params {};
    params.mode = options.init_image_path.empty() ? ENCLAVE_DIFFUSION_MODE_TXT2IMG : ENCLAVE_DIFFUSION_MODE_IMG2IMG;
    params.prompt = options.prompt.c_str();
    params.negative_prompt = options.negative_prompt.c_str();
    params.width = options.width;
    params.height = options.height;
    params.steps = options.steps;
    params.cfg_scale = options.cfg_scale;
    params.scheduler = options.scheduler.empty() ? nullptr : options.scheduler.c_str();
    params.seed = options.seed;
    params.denoise_strength = options.denoise_strength;
    params.init_image_path =
        options.init_image_path.empty() ? nullptr : options.init_image_path.c_str();
    params.memory_policy = ENCLAVE_MEMORY_POLICY_FULL;
    params.output_png_path = options.output_png.c_str();

    const int rc = enclave_diffusion_generate(
        handle,
        &params,
        [](int step, int total, void*) {
            std::cout << "[progress] " << step << '/' << total << '\n';
        },
        nullptr,
        err,
        kErrBuf);
    enclave_diffusion_destroy(handle);
    if (rc != 0) {
        return failStep("generate", err);
    }
    return passStep("generate", options.output_png);
#endif
}

}  // namespace enclave::harness
