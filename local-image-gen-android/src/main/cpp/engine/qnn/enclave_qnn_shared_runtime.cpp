#include "enclave_qnn_shared_runtime.h"

#include "qnn_log.h"

#include <dlfcn.h>

#include <chrono>
#include <cstring>
#include <thread>

namespace enclave::diffusion {
namespace {

bool devicePropertySupported(const EnclaveQnnApis& apis) {
    if (apis.core.propertyHasCapability == nullptr) {
        return false;
    }
    const auto status = apis.core.propertyHasCapability(QNN_PROPERTY_GROUP_DEVICE);
    return status != QNN_PROPERTY_NOT_SUPPORTED && status != QNN_PROPERTY_ERROR_UNKNOWN_KEY;
}

QnnHtpDevice_Arch_t htpArchFromStubLibrary(const char* stub_library) {
    if (stub_library == nullptr || stub_library[0] == '\0') {
        return QNN_HTP_DEVICE_ARCH_NONE;
    }
    if (std::strstr(stub_library, "V89") != nullptr) {
        return QNN_HTP_DEVICE_ARCH_V89;
    }
    if (std::strstr(stub_library, "V85") != nullptr) {
        return QNN_HTP_DEVICE_ARCH_V85;
    }
    if (std::strstr(stub_library, "V81") != nullptr) {
        return QNN_HTP_DEVICE_ARCH_V81;
    }
    if (std::strstr(stub_library, "V79") != nullptr) {
        return QNN_HTP_DEVICE_ARCH_V79;
    }
    if (std::strstr(stub_library, "V75") != nullptr) {
        return QNN_HTP_DEVICE_ARCH_V75;
    }
    if (std::strstr(stub_library, "V73") != nullptr) {
        return QNN_HTP_DEVICE_ARCH_V73;
    }
    if (std::strstr(stub_library, "V69") != nullptr) {
        return QNN_HTP_DEVICE_ARCH_V69;
    }
    if (std::strstr(stub_library, "V68") != nullptr) {
        return QNN_HTP_DEVICE_ARCH_V68;
    }
    return QNN_HTP_DEVICE_ARCH_NONE;
}

bool createHtpDevice(
    EnclaveQnnApis& apis,
    QnnHtpDevice_Arch_t arch,
    Qnn_DeviceHandle_t& device) {
    device = nullptr;
    if (!devicePropertySupported(apis) || apis.core.deviceCreate == nullptr) {
        return true;
    }

    QnnHtpDevice_CustomConfig_t arch_config {};
    QnnHtpDevice_CustomConfig_t signed_config {};
    QnnDevice_Config_t arch_device_config = QNN_DEVICE_CONFIG_INIT;
    QnnDevice_Config_t signed_device_config = QNN_DEVICE_CONFIG_INIT;
    const QnnDevice_Config_t* device_configs[3] = {nullptr, nullptr, nullptr};
    uint32_t config_count = 0;

    if (arch != QNN_HTP_DEVICE_ARCH_NONE) {
        arch_config.option = QNN_HTP_DEVICE_CONFIG_OPTION_ARCH;
        arch_config.arch.deviceId = 0;
        arch_config.arch.arch = arch;
        arch_device_config.option = QNN_DEVICE_CONFIG_OPTION_CUSTOM;
        arch_device_config.customConfig = &arch_config;
        device_configs[config_count++] = &arch_device_config;
        ENCLAVE_QNN_LOGI("Requesting HTP device arch %u.", static_cast<unsigned>(arch));
    }

    signed_config.option = QNN_HTP_DEVICE_CONFIG_OPTION_SIGNEDPD;
    signed_config.useSignedProcessDomain.deviceId = 0;
    signed_config.useSignedProcessDomain.useSignedProcessDomain = false;
    signed_device_config.option = QNN_DEVICE_CONFIG_OPTION_CUSTOM;
    signed_device_config.customConfig = &signed_config;
    device_configs[config_count++] = &signed_device_config;

    const auto status = apis.core.deviceCreate(
        nullptr,
        config_count > 0 ? device_configs : nullptr,
        &device);
    if (status == QNN_SUCCESS) {
        ENCLAVE_QNN_LOGI("deviceCreate succeeded.");
        return true;
    }
    if (status == QNN_DEVICE_ERROR_UNSUPPORTED_FEATURE) {
        return true;
    }
    ENCLAVE_QNN_LOGW("deviceCreate failed: %lu", static_cast<unsigned long>(status));
    device = nullptr;
    return true;
}

}  // namespace

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
    if (!createHtpDevice(apis_, htp_arch_, device_)) {
        error = "deviceCreate failed.";
        if (apis_.core.backendFree != nullptr && backend_ != nullptr) {
            apis_.core.backendFree(backend_);
            backend_ = nullptr;
        }
        releaseEnclaveQnnSystem(system_handle_);
        system_handle_ = nullptr;
        return false;
    }

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