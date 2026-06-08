#pragma once

#include "HTP/QnnHtpDevice.h"
#include "qnn_runtime.h"

#include <cstdint>
#include <string>

namespace enclave::diffusion {

/** One HTP backend + device shared across UNet/VAE context swaps in a generation. */
class EnclaveQnnSharedRuntime {
 public:
    static constexpr int kDspCooldownMs = 150;

    EnclaveQnnSharedRuntime() = default;
    ~EnclaveQnnSharedRuntime();

    EnclaveQnnSharedRuntime(const EnclaveQnnSharedRuntime&) = delete;
    EnclaveQnnSharedRuntime& operator=(const EnclaveQnnSharedRuntime&) = delete;

    bool ensureInitialized(
        const std::string& backend_path,
        const std::string& system_path,
        const std::string& dsp_library_path,
        const char* htp_stub_library,
        std::string& error);

    void shutdown();
    static void dspCooldown();

    bool isInitialized() const { return initialized_; }

    EnclaveQnnApis& apis() { return apis_; }
    const EnclaveQnnApis& apis() const { return apis_; }
    Qnn_BackendHandle_t backend() const { return backend_; }
    Qnn_DeviceHandle_t device() const { return device_; }

 private:
    EnclaveQnnApis apis_{};
    void* backend_handle_ = nullptr;
    void* system_handle_ = nullptr;
    Qnn_BackendHandle_t backend_ = nullptr;
    Qnn_DeviceHandle_t device_ = nullptr;
    QnnHtpDevice_Arch_t htp_arch_ = QNN_HTP_DEVICE_ARCH_NONE;
    bool initialized_ = false;
};

}  // namespace enclave::diffusion