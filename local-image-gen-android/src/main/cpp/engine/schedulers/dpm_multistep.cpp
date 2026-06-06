#include "scheduler.h"

#include <cmath>
#include <stdexcept>
#include <string>

namespace enclave::diffusion {
namespace {

class DpmMultistepScheduler : public Scheduler {
 public:
    void setTimesteps(int num_inference_steps) override {
        timesteps_.clear();
        sigmas_.clear();
        if (num_inference_steps <= 0) {
            throw std::invalid_argument("num_inference_steps must be positive");
        }
        const int step_ratio = kTrainSteps / (num_inference_steps + 1);
        for (int i = 0; i < num_inference_steps; ++i) {
            const int t = (num_inference_steps - i) * step_ratio;
            timesteps_.push_back(t);
            sigmas_.push_back(sigmaForTimestep(t));
        }
        sigmas_.push_back(0.0f);
    }

    int numTimesteps() const override { return static_cast<int>(timesteps_.size()); }

    int timestepAt(int step_index) const override { return timesteps_.at(step_index); }

    float sigmaAt(int step_index) const override { return sigmas_.at(step_index); }

    float sigmaNextAt(int step_index) const override { return sigmas_.at(step_index + 1); }

    std::vector<float> addNoise(
        const std::vector<float>& original,
        const std::vector<float>& noise,
        float denoise_strength) const override {
        if (original.size() != noise.size()) {
            throw std::invalid_argument("addNoise size mismatch");
        }
        const int t = static_cast<int>(denoise_strength * static_cast<float>(kTrainSteps));
        const float sigma = sigmaForTimestep(t);
        const float alpha = 1.0f / std::sqrt(1.0f + sigma * sigma);
        const float beta = 1.0f - alpha;
        std::vector<float> out(original.size());
        for (size_t i = 0; i < out.size(); ++i) {
            out[i] = alpha * original[i] + beta * noise[i];
        }
        return out;
    }

    std::vector<float> step(
        const std::vector<float>& sample,
        const std::vector<float>& model_output,
        int step_index) override {
        const float sigma = sigmaAt(step_index);
        const float sigma_next = sigmaNextAt(step_index);
        const float dt = sigma_next - sigma;
        std::vector<float> out(sample.size());
        for (size_t i = 0; i < out.size(); ++i) {
            out[i] = sample[i] + dt * model_output[i];
        }
        return out;
    }

 private:
    static constexpr int kTrainSteps = 1000;

    static float sigmaForTimestep(int t) {
        const float alpha_bar = std::pow(0.999f, static_cast<float>(t));
        return std::sqrt((1.0f - alpha_bar) / std::max(alpha_bar, 1e-8f));
    }

    std::vector<int> timesteps_;
    std::vector<float> sigmas_;
};

}  // namespace

Scheduler* createDpmScheduler() { return new DpmMultistepScheduler(); }

}  // namespace enclave::diffusion
