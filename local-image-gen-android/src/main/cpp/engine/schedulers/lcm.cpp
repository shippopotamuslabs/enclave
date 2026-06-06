#include "scheduler.h"

#include <algorithm>
#include <cmath>
#include <random>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

namespace enclave::diffusion {
namespace {

constexpr int kTrainSteps = 1000;
constexpr int kOriginalSteps = 50;
constexpr float kBetaStart = 0.00085f;
constexpr float kBetaEnd = 0.012f;
constexpr float kTimestepScaling = 10.0f;
constexpr float kSigmaData = 0.5f;

std::vector<float> buildScaledLinearBetas(int count) {
    std::vector<float> betas(static_cast<size_t>(count));
    const float beta_start_sqrt = std::sqrt(kBetaStart);
    const float beta_end_sqrt = std::sqrt(kBetaEnd);
    for (int i = 0; i < count; ++i) {
        const float t = static_cast<float>(i) / static_cast<float>(count - 1);
        const float v = beta_start_sqrt + (beta_end_sqrt - beta_start_sqrt) * t;
        betas[static_cast<size_t>(i)] = v * v;
    }
    return betas;
}

std::vector<float> buildAlphasCumprod(const std::vector<float>& betas) {
    std::vector<float> alphas_cumprod(betas.size());
    float running = 1.0f;
    for (size_t i = 0; i < betas.size(); ++i) {
        running *= 1.0f - betas[i];
        alphas_cumprod[i] = running;
    }
    return alphas_cumprod;
}

std::pair<float, float> boundaryScalings(int timestep) {
    const float scaled_timestep = static_cast<float>(timestep) * kTimestepScaling;
    const float denom = scaled_timestep * scaled_timestep + kSigmaData * kSigmaData;
    const float c_skip = (kSigmaData * kSigmaData) / denom;
    const float c_out = scaled_timestep / std::sqrt(denom);
    return {c_skip, c_out};
}

class LcmScheduler : public Scheduler {
 public:
    LcmScheduler() : alphas_cumprod_(buildAlphasCumprod(buildScaledLinearBetas(kTrainSteps))) {}

    void setSeed(int64_t seed) override { seed_ = seed; }

    float initNoiseSigma() const override { return 1.0f; }

    void setTimesteps(int num_inference_steps) override {
        timesteps_.clear();
        if (num_inference_steps <= 0) {
            throw std::invalid_argument("num_inference_steps must be positive");
        }
        if (num_inference_steps > kTrainSteps || num_inference_steps > kOriginalSteps) {
            throw std::invalid_argument("num_inference_steps exceeds LCM limits");
        }

        const int k = kTrainSteps / kOriginalSteps;
        std::vector<int> origin(static_cast<size_t>(kOriginalSteps));
        for (int i = 0; i < kOriginalSteps; ++i) {
            origin[static_cast<size_t>(i)] = (i + 1) * k - 1;
        }
        std::reverse(origin.begin(), origin.end());

        timesteps_.reserve(static_cast<size_t>(num_inference_steps));
        for (int i = 0; i < num_inference_steps; ++i) {
            const float idx_f =
                static_cast<float>(i) * static_cast<float>(origin.size()) /
                static_cast<float>(num_inference_steps);
            int idx = static_cast<int>(std::floor(idx_f));
            if (idx >= static_cast<int>(origin.size())) {
                idx = static_cast<int>(origin.size()) - 1;
            }
            timesteps_.push_back(origin[static_cast<size_t>(idx)]);
        }
    }

    int numTimesteps() const override { return static_cast<int>(timesteps_.size()); }

    int timestepAt(int step_index) const override { return timesteps_.at(static_cast<size_t>(step_index)); }

    float sigmaAt(int step_index) const override {
        const int t = timestepAt(step_index);
        const float alpha_bar = alphas_cumprod_[static_cast<size_t>(t)];
        return std::sqrt((1.0f - alpha_bar) / std::max(alpha_bar, 1e-8f));
    }

    float sigmaNextAt(int step_index) const override {
        if (step_index + 1 >= numTimesteps()) {
            return 0.0f;
        }
        return sigmaAt(step_index + 1);
    }

    std::vector<float> addNoise(
        const std::vector<float>& original,
        const std::vector<float>& noise,
        float denoise_strength) const override {
        if (original.size() != noise.size()) {
            throw std::invalid_argument("addNoise size mismatch");
        }
        const int t = static_cast<int>(denoise_strength * static_cast<float>(kTrainSteps));
        const float alpha = alphas_cumprod_[static_cast<size_t>(std::clamp(t, 0, kTrainSteps - 1))];
        const float sqrt_alpha = std::sqrt(alpha);
        const float sqrt_one_minus_alpha = std::sqrt(1.0f - alpha);
        std::vector<float> out(original.size());
        for (size_t i = 0; i < out.size(); ++i) {
            out[i] = sqrt_alpha * original[i] + sqrt_one_minus_alpha * noise[i];
        }
        return out;
    }

    std::vector<float> step(
        const std::vector<float>& sample,
        const std::vector<float>& model_output,
        int step_index) override {
        if (sample.size() != model_output.size()) {
            throw std::invalid_argument("LCM step size mismatch");
        }
        const int timestep = timestepAt(step_index);
        int prev_timestep = timestep;
        const int prev_step_index = step_index + 1;
        if (prev_step_index < numTimesteps()) {
            prev_timestep = timestepAt(prev_step_index);
        }

        const float alpha_prod_t = alphas_cumprod_[static_cast<size_t>(timestep)];
        const float alpha_prod_t_prev =
            prev_timestep >= 0 ? alphas_cumprod_[static_cast<size_t>(prev_timestep)] : 1.0f;
        const float beta_prod_t = 1.0f - alpha_prod_t;
        const float beta_prod_t_prev = 1.0f - alpha_prod_t_prev;

        const auto [c_skip, c_out] = boundaryScalings(timestep);

        std::vector<float> predicted_original(sample.size());
        const float sqrt_alpha = std::sqrt(alpha_prod_t);
        const float sqrt_beta = std::sqrt(beta_prod_t);
        for (size_t i = 0; i < sample.size(); ++i) {
            predicted_original[i] = (sample[i] - sqrt_beta * model_output[i]) / sqrt_alpha;
        }

        std::vector<float> denoised(sample.size());
        for (size_t i = 0; i < sample.size(); ++i) {
            denoised[i] = c_out * predicted_original[i] + c_skip * sample[i];
        }

        if (step_index == numTimesteps() - 1) {
            return denoised;
        }

        std::vector<float> prev_sample(sample.size());
        std::mt19937_64 rng(static_cast<uint64_t>(seed_) ^
                            static_cast<uint64_t>(step_index) * 0x9E3779B97F4A7C15ULL);
        std::normal_distribution<float> normal(0.0f, 1.0f);
        const float sqrt_alpha_prev = std::sqrt(alpha_prod_t_prev);
        const float sqrt_beta_prev = std::sqrt(beta_prod_t_prev);
        for (size_t i = 0; i < sample.size(); ++i) {
            prev_sample[i] = sqrt_alpha_prev * denoised[i] + sqrt_beta_prev * normal(rng);
        }
        return prev_sample;
    }

 private:
    std::vector<float> alphas_cumprod_;
    std::vector<int> timesteps_;
    int64_t seed_ = 0;
};

}  // namespace

Scheduler* createLcmScheduler() { return new LcmScheduler(); }

}  // namespace enclave::diffusion
