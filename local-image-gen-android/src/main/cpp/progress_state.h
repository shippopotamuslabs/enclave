#pragma once

#include <atomic>

namespace local_image_gen {
inline std::atomic<int> g_progress_step{0};
inline std::atomic<int> g_progress_total{0};

inline void resetProgress(int total_steps) {
    g_progress_step.store(0);
    g_progress_total.store(total_steps > 0 ? total_steps : 1);
}

inline void reportProgress(int step, int total_steps) {
    g_progress_step.store(step);
    g_progress_total.store(total_steps);
}

inline void readProgress(int* step_out, int* total_out) {
    if (step_out != nullptr) {
        *step_out = g_progress_step.load();
    }
    if (total_out != nullptr) {
        *total_out = g_progress_total.load();
    }
}
}  // namespace local_image_gen