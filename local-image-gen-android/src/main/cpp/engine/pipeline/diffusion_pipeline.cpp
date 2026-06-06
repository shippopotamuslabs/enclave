#include "engine/include/enclave_diffusion.h"

// The public pipeline entrypoint is implemented in enclave_diffusion_engine.cpp.
// This translation unit is intentionally present so downstream builds can attach
// future pipeline-specialized instrumentation without changing the C API target.
