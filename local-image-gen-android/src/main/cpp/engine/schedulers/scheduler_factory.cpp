#include "scheduler.h"

#include <cstring>

namespace enclave::diffusion {

Scheduler* createDpmScheduler();
Scheduler* createLcmScheduler();

Scheduler* createScheduler(const char* name, bool sdxl) {
    if (name == nullptr || std::strlen(name) == 0) {
        return sdxl ? createLcmScheduler() : createDpmScheduler();
    }
    if (std::strcmp(name, "lcm") == 0) {
        return createLcmScheduler();
    }
    return createDpmScheduler();
}

}  // namespace enclave::diffusion
