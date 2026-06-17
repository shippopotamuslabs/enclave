#pragma once

#include "harness_config.h"

#include <string>

namespace enclave::harness {

int runValidate(const HarnessOptions& options);
int runQnnMetadata(const HarnessOptions& options);
int runClipProbe(const HarnessOptions& options);
int runQnnInit(const HarnessOptions& options);
int runGenerate(HarnessOptions& options);

}  // namespace enclave::harness
