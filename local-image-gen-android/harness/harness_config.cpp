#include "harness_config.h"

#include "engine/runners/qnn_graph_runner.h"

#include <cstdlib>
#include <cstring>
#include <iostream>
#include <sys/stat.h>

namespace enclave::harness {
namespace {

bool pathExists(const std::string& path) {
    struct stat st {};
    return stat(path.c_str(), &st) == 0;
}

std::string envOrEmpty(const char* name) {
    const char* value = std::getenv(name);
    return value != nullptr ? value : "";
}

std::string joinPath(const std::string& dir, const char* leaf) {
    if (dir.empty()) {
        return leaf;
    }
    if (dir.back() == '/') {
        return dir + leaf;
    }
    return dir + "/" + leaf;
}

EnclaveModelFamily detectFamily(const std::string& model_dir) {
    if (pathExists(joinPath(model_dir, "clip_2.mnn"))) {
        if (pathExists(joinPath(model_dir, "vae_encoder.bin"))) {
            return ENCLAVE_MODEL_JUGGERNAUT_XL_QNN;
        }
        return ENCLAVE_MODEL_SDXL_QNN;
    }
    if (pathExists(joinPath(model_dir, "unet.mnn"))) {
        return ENCLAVE_MODEL_SD15_MNN;
    }
    return ENCLAVE_MODEL_SD15_QNN;
}

const char* defaultQnnSdkRoot() {
    static const char* kDefault = "/opt/qcom/aistack/qairt/2.47.0.260601";
    const char* from_env = std::getenv("QNN_SDK_ROOT");
    return from_env != nullptr && from_env[0] != '\0' ? from_env : kDefault;
}

std::string defaultHostSystemPath() {
    return joinPath(joinPath(defaultQnnSdkRoot(), "lib/x86_64-linux-clang"), "libQnnSystem.so");
}

std::string defaultHostBackendPath() {
    return joinPath(joinPath(defaultQnnSdkRoot(), "lib/x86_64-linux-clang"), "libQnnCpu.so");
}

bool needsValue(int index, int argc) {
    return index + 1 < argc;
}

}  // namespace

void printUsage(const char* program) {
    std::cout
        << "Usage: " << program << " [options]\n"
        << "\n"
        << "Steps (--step, default all):\n"
        << "  validate       Check model pack layout on disk\n"
        << "  qnn-metadata   Parse QNN binaries with libQnnSystem (host-safe)\n"
        << "  clip           Run tokenizer + MNN CLIP encode\n"
        << "  qnn-init       Load UNet QNN context (needs HTP + DSP on device)\n"
        << "  generate       Full txt2img pipeline\n"
        << "  all            validate -> qnn-metadata -> clip -> qnn-init -> generate\n"
        << "\n"
        << "Options:\n"
        << "  --model-dir PATH          Model pack directory (required)\n"
        << "  --qnn-runtime-dir PATH    Directory with libQnnHtp.so / libQnnSystem.so\n"
        << "  --dsp-library-path PATH   Skel search path (defaults to qnn-runtime-dir)\n"
        << "  --family sd15|sd15-cpu|sdxl|juggernaut  Override auto-detected family\n"
        << "  --prompt TEXT\n"
        << "  --negative-prompt TEXT\n"
        << "  --output PATH             Output PNG for generate\n"
        << "  --width --height --steps --cfg --scheduler --seed\n"
        << "  --denoise-strength F      img2img noise strength (default 0.75)\n"
        << "  --init-image PATH         img2img input\n"
        << "  --help\n";
}

bool parseArgs(int argc, char** argv, HarnessOptions& out, std::string& error) {
    for (int i = 1; i < argc; ++i) {
        const char* arg = argv[i];
        if (std::strcmp(arg, "--help") == 0 || std::strcmp(arg, "-h") == 0) {
            printUsage(argv[0]);
            std::exit(0);
        }
        if (std::strcmp(arg, "--model-dir") == 0) {
            if (!needsValue(i, argc)) {
                error = "--model-dir requires a value.";
                return false;
            }
            out.model_dir = argv[++i];
            continue;
        }
        if (std::strcmp(arg, "--qnn-runtime-dir") == 0) {
            if (!needsValue(i, argc)) {
                error = "--qnn-runtime-dir requires a value.";
                return false;
            }
            out.qnn_runtime_dir = argv[++i];
            continue;
        }
        if (std::strcmp(arg, "--dsp-library-path") == 0) {
            if (!needsValue(i, argc)) {
                error = "--dsp-library-path requires a value.";
                return false;
            }
            out.dsp_library_path = argv[++i];
            continue;
        }
        if (std::strcmp(arg, "--family") == 0) {
            if (!needsValue(i, argc)) {
                error = "--family requires a value.";
                return false;
            }
            const char* value = argv[++i];
            if (std::strcmp(value, "sdxl") == 0) {
                out.family = ENCLAVE_MODEL_SDXL_QNN;
                out.family_explicit = true;
            } else if (std::strcmp(value, "juggernaut") == 0) {
                out.family = ENCLAVE_MODEL_JUGGERNAUT_XL_QNN;
                out.family_explicit = true;
            } else if (std::strcmp(value, "sd15") == 0) {
                out.family = ENCLAVE_MODEL_SD15_QNN;
                out.family_explicit = true;
            } else if (std::strcmp(value, "sd15-cpu") == 0) {
                out.family = ENCLAVE_MODEL_SD15_MNN;
                out.family_explicit = true;
            } else {
                error = "Unknown --family value.";
                return false;
            }
            continue;
        }
        if (std::strcmp(arg, "--step") == 0) {
            if (!needsValue(i, argc)) {
                error = "--step requires a value.";
                return false;
            }
            out.step = argv[++i];
            continue;
        }
        if (std::strcmp(arg, "--prompt") == 0) {
            if (!needsValue(i, argc)) {
                error = "--prompt requires a value.";
                return false;
            }
            out.prompt = argv[++i];
            continue;
        }
        if (std::strcmp(arg, "--negative-prompt") == 0) {
            if (!needsValue(i, argc)) {
                error = "--negative-prompt requires a value.";
                return false;
            }
            out.negative_prompt = argv[++i];
            continue;
        }
        if (std::strcmp(arg, "--output") == 0) {
            if (!needsValue(i, argc)) {
                error = "--output requires a value.";
                return false;
            }
            out.output_png = argv[++i];
            continue;
        }
        if (std::strcmp(arg, "--init-image") == 0) {
            if (!needsValue(i, argc)) {
                error = "--init-image requires a value.";
                return false;
            }
            out.init_image_path = argv[++i];
            continue;
        }
        if (std::strcmp(arg, "--width") == 0) {
            if (!needsValue(i, argc)) {
                error = "--width requires a value.";
                return false;
            }
            out.width = std::atoi(argv[++i]);
            continue;
        }
        if (std::strcmp(arg, "--height") == 0) {
            if (!needsValue(i, argc)) {
                error = "--height requires a value.";
                return false;
            }
            out.height = std::atoi(argv[++i]);
            continue;
        }
        if (std::strcmp(arg, "--steps") == 0) {
            if (!needsValue(i, argc)) {
                error = "--steps requires a value.";
                return false;
            }
            out.steps = std::atoi(argv[++i]);
            continue;
        }
        if (std::strcmp(arg, "--cfg") == 0) {
            if (!needsValue(i, argc)) {
                error = "--cfg requires a value.";
                return false;
            }
            out.cfg_scale = static_cast<float>(std::atof(argv[++i]));
            continue;
        }
        if (std::strcmp(arg, "--denoise-strength") == 0) {
            if (!needsValue(i, argc)) {
                error = "--denoise-strength requires a value.";
                return false;
            }
            out.denoise_strength = static_cast<float>(std::atof(argv[++i]));
            continue;
        }
        if (std::strcmp(arg, "--scheduler") == 0) {
            if (!needsValue(i, argc)) {
                error = "--scheduler requires a value.";
                return false;
            }
            out.scheduler = argv[++i];
            continue;
        }
        if (std::strcmp(arg, "--seed") == 0) {
            if (!needsValue(i, argc)) {
                error = "--seed requires a value.";
                return false;
            }
            out.seed = std::atoll(argv[++i]);
            continue;
        }
        error = std::string("Unknown argument: ") + arg;
        return false;
    }

    if (out.model_dir.empty()) {
        error = "--model-dir is required.";
        return false;
    }
    if (!out.family_explicit) {
        out.family = detectFamily(out.model_dir);
    }
    if (out.qnn_runtime_dir.empty()) {
#ifdef __ANDROID__
        out.qnn_runtime_dir = envOrEmpty("ENCLAVE_QNN_RUNTIME_DIR");
#else
        out.qnn_runtime_dir = defaultHostSystemPath().substr(
            0,
            defaultHostSystemPath().find_last_of('/'));
#endif
    }
    if (out.dsp_library_path.empty()) {
        out.dsp_library_path = out.qnn_runtime_dir;
    }
    return true;
}

enclave::diffusion::QnnRuntimePaths buildQnnPaths(const HarnessOptions& options) {
    enclave::diffusion::QnnRuntimePaths paths;
#ifdef __ANDROID__
    paths.backend_path = joinPath(options.qnn_runtime_dir, "libQnnHtp.so");
    paths.system_path = joinPath(options.qnn_runtime_dir, "libQnnSystem.so");
#else
    paths.backend_path = defaultHostBackendPath();
    paths.system_path = defaultHostSystemPath();
#endif
    paths.dsp_library_path = options.dsp_library_path;
    paths.htp_stub_library =
        options.family == ENCLAVE_MODEL_SD15_QNN ? "libQnnHtpV73Stub.so" : "libQnnHtpV79Stub.so";
    return paths;
}

EnclaveDiffusionConfig buildDiffusionConfig(HarnessOptions& options) {
    const auto qnn_paths = buildQnnPaths(options);
    options.resolved_backend_path = qnn_paths.backend_path;
    options.resolved_system_path = qnn_paths.system_path;
    EnclaveDiffusionConfig cfg {};
    cfg.model_dir = options.model_dir.c_str();
    cfg.family = options.family;
    cfg.qnn_backend_path = options.resolved_backend_path.c_str();
    cfg.qnn_system_path = options.resolved_system_path.c_str();
    cfg.dsp_library_path = options.dsp_library_path.c_str();
    cfg.native_lib_dir = options.qnn_runtime_dir.c_str();
    return cfg;
}

}  // namespace enclave::harness
