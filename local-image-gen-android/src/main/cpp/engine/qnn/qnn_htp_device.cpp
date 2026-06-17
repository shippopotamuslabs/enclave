#include "qnn_htp_device.h"

#include "qnn_log.h"

#include <cstring>

namespace enclave::diffusion {
namespace {

bool devicePropertySupported(const EnclaveQnnApis& apis) {
    if (apis.core.propertyHasCapability == nullptr) {
        return false;
    }
    const auto status = apis.core.propertyHasCapability(QNN_PROPERTY_GROUP_DEVICE);
    return status != QNN_PROPERTY_NOT_SUPPORTED && status != QNN_PROPERTY_ERROR_UNKNOWN_KEY;
}

}  // namespace

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

void createHtpDevice(
    EnclaveQnnApis& apis,
    QnnHtpDevice_Arch_t arch,
    Qnn_DeviceHandle_t& device) {
    device = nullptr;
    if (!devicePropertySupported(apis) || apis.core.deviceCreate == nullptr) {
        return;
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

    const auto status = apis.core.deviceCreate(nullptr, device_configs, &device);
    if (status == QNN_SUCCESS) {
        ENCLAVE_QNN_LOGI("deviceCreate succeeded.");
        return;
    }
    if (status != QNN_DEVICE_ERROR_UNSUPPORTED_FEATURE) {
        ENCLAVE_QNN_LOGW("deviceCreate failed: %lu", static_cast<unsigned long>(status));
    }
    device = nullptr;
}

}  // namespace enclave::diffusion
