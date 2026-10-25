#pragma once

#include <string>
#include <vector>

namespace enclave::diffusion {

/** Placeholders when ENCLAVE_DIFFUSION_HAS_QNN is off (unique_ptr needs complete types). */
class EnclaveQnnApp {
 public:
    EnclaveQnnApp() = default;
    ~EnclaveQnnApp() = default;

    // QnnGraphRunner::load* call this unguarded after a successful
    // loadGraph; in this configuration loadGraph always fails first, so
    // the call is unreachable at runtime but must compile.
    void setSpatialSize(int output_width, int output_height) {
        (void)output_width;
        (void)output_height;
    }
};

/** Placeholder so QnnGraphRunner's unique_ptr member has a complete type. */
class EnclaveQnnSharedRuntime {
 public:
    EnclaveQnnSharedRuntime() = default;
    ~EnclaveQnnSharedRuntime() = default;
};

}  // namespace enclave::diffusion
