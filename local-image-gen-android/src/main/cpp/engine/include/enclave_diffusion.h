#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    ENCLAVE_DIFFUSION_MODE_TXT2IMG = 0,
    ENCLAVE_DIFFUSION_MODE_IMG2IMG = 1,
    ENCLAVE_DIFFUSION_MODE_INPAINT = 2,
} EnclaveDiffusionMode;

typedef enum {
    ENCLAVE_MEMORY_POLICY_FULL = 0,
    ENCLAVE_MEMORY_POLICY_LOW_RAM = 1,
} EnclaveMemoryPolicy;

typedef enum {
    ENCLAVE_MODEL_SD15_QNN = 0,
    ENCLAVE_MODEL_SDXL_QNN = 1,
    ENCLAVE_MODEL_SD15_MNN = 2,
    ENCLAVE_MODEL_JUGGERNAUT_XL_QNN = 3,
} EnclaveModelFamily;

typedef struct EnclaveDiffusionHandle EnclaveDiffusionHandle;

typedef struct {
    const char* model_dir;
    EnclaveModelFamily family;
    const char* qnn_backend_path;
    const char* qnn_system_path;
    const char* dsp_library_path;
    const char* native_lib_dir;
} EnclaveDiffusionConfig;

typedef struct {
    EnclaveDiffusionMode mode;
    const char* prompt;
    const char* negative_prompt;
    int width;
    int height;
    int steps;
    float cfg_scale;
    const char* scheduler;
    int64_t seed;
    float denoise_strength;
    const char* init_image_path;
    EnclaveMemoryPolicy memory_policy;
    const char* output_png_path;
} EnclaveGenerateParams;

typedef void (*EnclaveProgressFn)(int step, int total_steps, void* user);

EnclaveDiffusionHandle* enclave_diffusion_create(
    const EnclaveDiffusionConfig* cfg,
    char* err,
    int err_len);

void enclave_diffusion_destroy(EnclaveDiffusionHandle* handle);

int enclave_diffusion_generate(
    EnclaveDiffusionHandle* handle,
    const EnclaveGenerateParams* params,
    EnclaveProgressFn progress,
    void* user,
    char* err,
    int err_len);

void enclave_diffusion_cancel(EnclaveDiffusionHandle* handle);

void enclave_diffusion_on_trim_memory(EnclaveDiffusionHandle* handle, int level);

int enclave_diffusion_mnn_available(void);

#ifdef __cplusplus
}
#endif
