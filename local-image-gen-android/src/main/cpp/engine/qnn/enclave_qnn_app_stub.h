#pragma once

#include <string>
#include <vector>

namespace enclave::diffusion {

/** Placeholder when ENCLAVE_DIFFUSION_HAS_QNN is off (unique_ptr needs a complete type). */
class EnclaveQnnApp {
 public:
    EnclaveQnnApp() = default;
    ~EnclaveQnnApp() = default;
};

}  // namespace enclave::diffusion
