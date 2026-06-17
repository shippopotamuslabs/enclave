#pragma once

#include <string>

namespace enclave::diffusion {

/** Enable unsigned CDSP process domain for app-bundled QNN Hexagon skels. */
bool enableQnnUnsignedCdspProcessDomain(std::string& error);

}  // namespace enclave::diffusion
