#include "scheduler.h"

#include <cstring>

namespace enclave::diffusion {

Scheduler* createDpmScheduler(bool sdxl);
Scheduler* createLcmScheduler();

Scheduler* createScheduler(const char* name, bool sdxl) {
    if (name == nullptr || std::strlen(name) == 0) {
        return sdxl ? createLcmScheduler() : createDpmScheduler(sdxl);
    }
    if (std::strcmp(name, "lcm") == 0) {
        return createLcmScheduler();
    }
    return createDpmScheduler(sdxl);
}

}  // namespace enclave::diffusion
