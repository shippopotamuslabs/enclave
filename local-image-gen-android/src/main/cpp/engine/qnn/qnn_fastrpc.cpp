#include "qnn_fastrpc.h"

#include "qnn_log.h"

#include <cstdint>
#include <dlfcn.h>

namespace enclave::diffusion {
namespace {

constexpr uint32_t kDsprpcControlUnsignedModule = 2;
constexpr int kCdspDomainId = 3;

struct remote_rpc_control_unsigned_module {
    int domain;
    int enable;
};

using remote_session_control_fn = int (*)(uint32_t req, void* data, uint32_t datalen);

remote_session_control_fn resolveRemoteSessionControl() {
    auto* sym = reinterpret_cast<remote_session_control_fn>(dlsym(RTLD_DEFAULT, "remote_session_control"));
    if (sym != nullptr) {
        return sym;
    }
    void* cdsp = dlopen("libcdsprpc.so", RTLD_NOW | RTLD_NOLOAD);
    if (cdsp == nullptr) {
        return nullptr;
    }
    return reinterpret_cast<remote_session_control_fn>(dlsym(cdsp, "remote_session_control"));
}

}  // namespace

bool enableQnnUnsignedCdspProcessDomain(std::string& error) {
    const auto remote_session_control = resolveRemoteSessionControl();
    if (remote_session_control == nullptr) {
        error = "remote_session_control is unavailable in libcdsprpc.so.";
        return false;
    }

    remote_rpc_control_unsigned_module config {};
    config.domain = kCdspDomainId;
    config.enable = 1;
    const int status =
        remote_session_control(kDsprpcControlUnsignedModule, &config, sizeof(config));
    if (status != 0) {
        error = "remote_session_control(unsigned CDSP PD) failed with code " + std::to_string(status);
        ENCLAVE_QNN_LOGE("%s", error.c_str());
        return false;
    }

    ENCLAVE_QNN_LOGI("Enabled unsigned CDSP process domain for QNN HTP.");
    return true;
}

}  // namespace enclave::diffusion
