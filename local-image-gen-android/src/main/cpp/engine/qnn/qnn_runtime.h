#pragma once

#include "QnnInterface.h"
#include "System/QnnSystemInterface.h"

#include <cstdint>
#include <string>

namespace enclave::diffusion {

/** Resolved QNN core + system API tables from dlopen'd backend/system libs. */
struct EnclaveQnnApis {
    QNN_INTERFACE_VER_TYPE core {};
    QNN_SYSTEM_INTERFACE_VER_TYPE system {};
};

void setDspLibraryPath(const std::string& dsp_library_path);

bool preloadQnnHtpLibraries(
    const std::string& runtime_dir,
    const char* htp_stub_library,
    std::string& error);

bool loadEnclaveQnnBackend(
    const std::string& backend_path,
    EnclaveQnnApis& apis,
    void** backend_handle_out,
    std::string& error);

bool acquireEnclaveQnnSystem(
    const std::string& system_path,
    EnclaveQnnApis& apis,
    void** system_handle_out,
    std::string& error);

void releaseEnclaveQnnSystem(void* system_handle);

}  // namespace enclave::diffusion
