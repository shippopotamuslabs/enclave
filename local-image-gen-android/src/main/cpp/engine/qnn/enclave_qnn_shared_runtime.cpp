#include "enclave_qnn_shared_runtime.h"

#include "qnn_htp_device.h"
#include "qnn_log.h"

#include <dlfcn.h>

#include <chrono>
#include <thread>

namespace enclave::diffusion {

EnclaveQnnSharedRuntime::~EnclaveQnnSharedRuntime() { shutdown(); }

void EnclaveQnnSharedRuntime::dspCooldown() {
    if (kDspCooldownMs <= 0) {
        return;
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(kDspCooldownMs));
}

bool EnclaveQnnSharedRuntime::ensureInitialized(
    const std::string& backend_path,
    const std::string& system_path,
    const std::string& dsp_library_path,
    const char* htp_stub_library,
    std::string& error) {
    if (initialized_) {
        return true;
    }

    setDspLibraryPath(dsp_library_path);
    if (!preloadQnnHtpLibraries(dsp_library_path, htp_stub_library, error)) {
        return false;
    }

    htp_arch_ = htpArchFromStubLibrary(htp_stub_library);
    if (!acquireEnclaveQnnSystem(system_path, apis_, &system_handle_, error)) {
        return false;
    }
    if (!loadEnclaveQnnBackend(backend_path, apis_, &backend_handle_, error)) {
        releaseEnclaveQnnSystem(system_handle_);
        system_handle_ = nullptr;
        return false;
    }
    if (apis_.core.backendCreate == nullptr) {
        error = "backendCreate is unavailable.";
        releaseEnclaveQnnSystem(system_handle_);
        system_handle_ = nullptr;
        return false;
    }
    if (apis_.core.backendCreate(nullptr, nullptr, &backend_) != QNN_BACKEND_NO_ERROR) {
        error = "backendCreate failed.";
        releaseEnclaveQnnSystem(system_handle_);
        system_handle_ = nullptr;
        return false;
    }
    createHtpDevice(apis_, htp_arch_, device_);

    initialized_ = true;
    ENCLAVE_QNN_LOGI("Shared QNN runtime initialized.");
    return true;
}

void EnclaveQnnSharedRuntime::shutdown() {
    if (!initialized_) {
        return;
    }
    dspCooldown();
    if (device_ != nullptr && apis_.core.deviceFree != nullptr) {
        apis_.core.deviceFree(device_);
        device_ = nullptr;
    }
    if (backend_ != nullptr && apis_.core.backendFree != nullptr) {
        apis_.core.backendFree(backend_);
        backend_ = nullptr;
    }
    if (backend_handle_ != nullptr) {
        dlclose(backend_handle_);
        backend_handle_ = nullptr;
    }
    if (system_handle_ != nullptr) {
        releaseEnclaveQnnSystem(system_handle_);
        system_handle_ = nullptr;
    }
    apis_ = {};
    initialized_ = false;
    ENCLAVE_QNN_LOGI("Shared QNN runtime shut down.");
}

}  // namespace enclave::diffusion