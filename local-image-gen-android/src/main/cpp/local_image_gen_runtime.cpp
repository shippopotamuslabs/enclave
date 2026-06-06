#include "local_image_gen_runtime.h"

#include <android/log.h>
#include <dlfcn.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstdlib>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <string>
#include <vector>

#define LOG_TAG "local-image-gen-runtime"

namespace local_image_gen {
namespace {

std::string g_native_lib_dir;
std::string g_qnn_runtime_dir;

const char* kQnnRuntimeLibNames[] = {
    "libQnnHtp.so",
    "libQnnSystem.so",
    "libQnnHtpPrepare.so",
    "libQnnHtpV68Stub.so",
    "libQnnHtpV69Stub.so",
    "libQnnHtpV73Stub.so",
    "libQnnHtpV75Stub.so",
    "libQnnHtpV79Stub.so",
    "libQnnHtpV81Stub.so",
    "libQnnHtpV68.so",
    "libQnnHtpV68Skel.so",
    "libQnnHtpV69.so",
    "libQnnHtpV69Skel.so",
    "libQnnHtpV73.so",
    "libQnnHtpV73Skel.so",
    "libQnnHtpV75.so",
    "libQnnHtpV75Skel.so",
    "libQnnHtpV79.so",
    "libQnnHtpV79Skel.so",
    "libQnnHtpV81.so",
    "libQnnHtpV81Skel.so",
};

bool file_exists(const std::string& path) {
    struct stat st {};
    return stat(path.c_str(), &st) == 0 && S_ISREG(st.st_mode);
}

bool copy_file_if_needed(const std::string& src, const std::string& dst) {
    if (!file_exists(src)) {
        return false;
    }
    if (file_exists(dst)) {
        struct stat src_st {};
        struct stat dst_st {};
        if (stat(src.c_str(), &src_st) == 0 && stat(dst.c_str(), &dst_st) == 0 &&
            src_st.st_size == dst_st.st_size) {
            return true;
        }
    }
    std::ifstream in(src, std::ios::binary);
    std::ofstream out(dst, std::ios::binary | std::ios::trunc);
    if (!in || !out) {
        return false;
    }
    out << in.rdbuf();
    chmod(dst.c_str(), 0755);
    return file_exists(dst);
}

void copy_error(const char* msg, char* error_out, int error_out_len) {
    if (error_out == nullptr || error_out_len <= 0) {
        return;
    }
    std::strncpy(error_out, msg, static_cast<size_t>(error_out_len - 1));
    error_out[error_out_len - 1] = '\0';
}

}  // namespace

void apply_qnn_hexagon_environment() {
    setenv("DSP_LIBRARY_PATH", g_qnn_runtime_dir.c_str(), 1);
    setenv("ADSP_LIBRARY_PATH", g_qnn_runtime_dir.c_str(), 1);
    setenv("CDSP_LIBRARY_PATH", g_qnn_runtime_dir.c_str(), 1);
    setenv("LD_LIBRARY_PATH", g_qnn_runtime_dir.c_str(), 1);
}

void set_runtime_paths(const char* native_lib_dir, const char* qnn_runtime_dir) {
    g_native_lib_dir = native_lib_dir != nullptr ? native_lib_dir : "";
    g_qnn_runtime_dir = qnn_runtime_dir != nullptr ? qnn_runtime_dir : "";
    apply_qnn_hexagon_environment();
}

const std::string& native_lib_dir() { return g_native_lib_dir; }

const std::string& qnn_runtime_dir() { return g_qnn_runtime_dir; }

bool file_exists_in_dir(const std::string& dir, const char* filename) {
    if (dir.empty() || filename == nullptr) {
        return false;
    }
    return file_exists(dir + "/" + filename);
}

int probe_qnn_libs() {
    if (g_native_lib_dir.empty() && g_qnn_runtime_dir.empty()) {
        return 0;
    }
    auto has = [&](const char* name) -> bool {
        if (!g_qnn_runtime_dir.empty() && file_exists(g_qnn_runtime_dir + "/" + name)) {
            return true;
        }
        if (!g_native_lib_dir.empty() && file_exists(g_native_lib_dir + "/" + name)) {
            return true;
        }
        return false;
    };
    if (!has("libQnnHtp.so") || !has("libQnnSystem.so")) {
        return 0;
    }
    if (!has("libQnnHtpPrepare.so")) {
        return 1;
    }
    return 2;
}

int probe_mnn_libs() {
    if (g_native_lib_dir.empty()) {
        return 0;
    }
    void* handle = dlopen((g_native_lib_dir + "/libMNN.so").c_str(), RTLD_NOW | RTLD_LOCAL);
    if (handle == nullptr) {
        return 0;
    }
    dlclose(handle);
    return 1;
}

bool prepare_qnn_runtime_libs(char* error_out, int error_out_len) {
    if (g_qnn_runtime_dir.empty()) {
        copy_error("Native paths not initialized.", error_out, error_out_len);
        return false;
    }
    const bool runtime_is_native_lib_dir =
        !g_native_lib_dir.empty() && g_qnn_runtime_dir == g_native_lib_dir;
    if (!runtime_is_native_lib_dir) {
        std::error_code ec;
        std::filesystem::create_directories(g_qnn_runtime_dir, ec);
        for (const char* name : kQnnRuntimeLibNames) {
            const std::string dst = g_qnn_runtime_dir + "/" + name;
            if (!g_native_lib_dir.empty()) {
                const std::string src = g_native_lib_dir + "/" + name;
                if (file_exists(src)) {
                    if (!copy_file_if_needed(src, dst)) {
                        copy_error("Failed to copy QNN runtime library.", error_out, error_out_len);
                        return false;
                    }
                }
            }
            if (file_exists(dst)) {
                chmod(dst.c_str(), 0755);
            }
        }
    }
    apply_qnn_hexagon_environment();
    return true;
}

bool qnn_model_dir_ready(const char* model_dir, char* error_out, int error_out_len) {
    if (model_dir == nullptr || model_dir[0] == '\0') {
        copy_error("Model directory is empty.", error_out, error_out_len);
        return false;
    }
    const std::string model_dir_str(model_dir);
    const bool sdxl_layout = file_exists(model_dir_str + "/clip_2.mnn");
    static const char* kSd15Required[] = {
        "clip_v2.mnn",
        "token_emb.bin",
        "pos_emb.bin",
        "unet.bin",
        "vae_decoder.bin",
        "tokenizer.json",
    };
    static const char* kSdxlRequired[] = {
        "clip.mnn",
        "clip_2.mnn",
        "token_emb.bin",
        "token_emb_2.bin",
        "pos_emb.bin",
        "pos_emb_2.bin",
        "unet.bin",
        "vae_decoder.bin",
        "tokenizer.json",
    };
    if (sdxl_layout) {
        for (const char* name : kSdxlRequired) {
            if (!file_exists(model_dir_str + "/" + name)) {
                copy_error("Missing QNN model file in model directory.", error_out, error_out_len);
                return false;
            }
        }
    } else {
        for (const char* name : kSd15Required) {
            if (!file_exists(model_dir_str + "/" + name)) {
                copy_error("Missing QNN model file in model directory.", error_out, error_out_len);
                return false;
            }
        }
    }
    return true;
}

bool mnn_model_dir_ready(const char* model_dir, char* error_out, int error_out_len) {
    if (model_dir == nullptr || model_dir[0] == '\0') {
        copy_error("Model directory is empty.", error_out, error_out_len);
        return false;
    }
    const char* required[] = {"clip.mnn", "unet.mnn", "vae_decoder.mnn", "tokenizer.json"};
    for (const char* name : required) {
        const std::string path = std::string(model_dir) + "/" + name;
        if (!file_exists(path)) {
            copy_error("Missing MNN model file in model directory.", error_out, error_out_len);
            return false;
        }
    }
    return true;
}

}  // namespace local_image_gen
