#pragma once

#include "enclave_diffusion.h"
#include "stage_loader.h"

namespace enclave::diffusion {

bool shouldUnloadAfterStage(PipelineStage stage, EnclaveMemoryPolicy policy);

bool shouldRetainTextEmbeddings(EnclaveMemoryPolicy policy);

}  // namespace enclave::diffusion
