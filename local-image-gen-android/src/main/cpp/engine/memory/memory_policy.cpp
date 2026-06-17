#include "memory_policy.h"

namespace enclave::diffusion {

bool shouldUnloadAfterStage(PipelineStage stage, EnclaveMemoryPolicy policy) {
    return policy == ENCLAVE_MEMORY_POLICY_LOW_RAM && stage != PipelineStage::UnetLoop;
}

bool shouldRetainTextEmbeddings(EnclaveMemoryPolicy policy) {
    return policy == ENCLAVE_MEMORY_POLICY_FULL || policy == ENCLAVE_MEMORY_POLICY_LOW_RAM;
}

}  // namespace enclave::diffusion
