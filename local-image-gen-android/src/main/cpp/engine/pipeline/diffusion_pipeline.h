#pragma once

#include <atomic>
#include <memory>
#include <string>

#include "enclave_diffusion.h"

namespace enclave::diffusion {

class DiffusionPipeline {
 public:
    explicit DiffusionPipeline(EnclaveDiffusionConfig config);
    ~DiffusionPipeline();

    int generate(
        const EnclaveGenerateParams& params,
        EnclaveProgressFn progress,
        void* user,
        char* err,
        int err_len);

    void cancel();
    void onTrimMemory(int level);

 private:
    EnclaveDiffusionConfig config_;
    std::atomic<bool> cancelled_{false};
};

}  // namespace enclave::diffusion
