#pragma once

#include <cstdint>
#include <vector>

namespace enclave::diffusion {

class Scheduler {
 public:
    virtual ~Scheduler() = default;
    virtual void setTimesteps(int num_inference_steps) = 0;
    virtual int numTimesteps() const = 0;
    virtual int timestepAt(int step_index) const = 0;
    virtual float sigmaAt(int step_index) const = 0;
    virtual float sigmaNextAt(int step_index) const = 0;
    virtual std::vector<float> addNoise(
        const std::vector<float>& original,
        const std::vector<float>& noise,
        float denoise_strength) const = 0;
    virtual std::vector<float> scale_model_input(
        const std::vector<float>& sample,
        int /*timestep*/) const {
        return sample;
    }

    virtual void setSeed(int64_t /*seed*/) {}

    virtual float initNoiseSigma() const { return 1.0f; }

    virtual std::vector<float> step(
        const std::vector<float>& sample,
        const std::vector<float>& model_output,
        int step_index) = 0;
};

Scheduler* createScheduler(const char* name, bool sdxl);

}  // namespace enclave::diffusion
