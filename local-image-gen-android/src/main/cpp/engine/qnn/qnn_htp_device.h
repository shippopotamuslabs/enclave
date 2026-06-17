#pragma once

#include "HTP/QnnHtpDevice.h"
#include "qnn_runtime.h"

namespace enclave::diffusion {

/** Maps an HTP stub library name (e.g. libQnnHtpV79Stub.so) to its device arch. */
QnnHtpDevice_Arch_t htpArchFromStubLibrary(const char* stub_library);

/**
 * Creates an HTP device with the requested arch and an unsigned process domain.
 * Device creation is best-effort: backends that do not support device config
 * leave `device` null and the caller proceeds without one.
 */
void createHtpDevice(
    EnclaveQnnApis& apis,
    QnnHtpDevice_Arch_t arch,
    Qnn_DeviceHandle_t& device);

}  // namespace enclave::diffusion
