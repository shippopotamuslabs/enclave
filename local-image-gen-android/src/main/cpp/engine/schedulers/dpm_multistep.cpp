#include "scheduler.h"

#include <algorithm>
#include <cmath>
#include <stdexcept>
#include <string>
#include <vector>

namespace enclave::diffusion {
namespace {

constexpr int kTrainSteps = 1000;
constexpr float kBetaStart = 0.00085f;
constexpr float kBetaEnd = 0.012f;

std::vector<float> buildScaledLinearBetas() {
    std::vector<float> betas(static_cast<size_t>(kTrainSteps));
    const float beta_start_sqrt = std::sqrt(kBetaStart);
    const float beta_end_sqrt = std::sqrt(kBetaEnd);
    for (int i = 0; i < kTrainSteps; ++i) {
        const float t = static_cast<float>(i) / static_cast<float>(kTrainSteps - 1);
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

std::vector<float> buildBaseSigmas(const std::vector<float>& alphas_cumprod) {
    std::vector<float> sigmas(alphas_cumprod.size());
    for (size_t i = 0; i < alphas_cumprod.size(); ++i) {
        const float alpha_cumprod = alphas_cumprod[i];
        sigmas[i] = std::sqrt((1.0f - alpha_cumprod) / std::max(alpha_cumprod, 1e-8f));
    }
    return sigmas;
}

float interpolateSigma(float timestep, const std::vector<float>& base_sigmas) {
    if (timestep <= 0.0f) {
        return base_sigmas.front();
    }
    if (timestep >= static_cast<float>(kTrainSteps - 1)) {
        return base_sigmas.back();
    }
    const int t_floor = static_cast<int>(std::floor(timestep));
    const int t_ceil = static_cast<int>(std::ceil(timestep));
    const float weight = timestep - static_cast<float>(t_floor);
    return base_sigmas[static_cast<size_t>(t_floor)] * (1.0f - weight) +
           base_sigmas[static_cast<size_t>(t_ceil)] * weight;
}

std::pair<float, float> sigmaToAlphaSigma(float sigma) {
    const float alpha_t = 1.0f / std::sqrt(sigma * sigma + 1.0f);
    const float sigma_t = sigma * alpha_t;
    return {alpha_t, sigma_t};
}

class DpmMultistepScheduler : public Scheduler {
 public:
    explicit DpmMultistepScheduler(bool sdxl)
        : sdxl_(sdxl),
          alphas_cumprod_(buildAlphasCumprod(buildScaledLinearBetas())),
          base_sigmas_(buildBaseSigmas(alphas_cumprod_)) {}

    void setTimesteps(int num_inference_steps) override {
        timesteps_.clear();
        sigmas_.clear();
        model_outputs_.clear();
        lower_order_nums_ = 0;
        if (num_inference_steps <= 0) {
            throw std::invalid_argument("num_inference_steps must be positive");
        }

        timesteps_.reserve(static_cast<size_t>(num_inference_steps));
        if (sdxl_) {
            const float step_ratio =
                static_cast<float>(kTrainSteps) / static_cast<float>(num_inference_steps);
            for (int i = 0; i < num_inference_steps; ++i) {
                const float t =
                    std::round(static_cast<float>(kTrainSteps) - static_cast<float>(i) * step_ratio) -
                    1.0f;
                timesteps_.push_back(static_cast<int>(t));
            }
        } else {
            const int step_ratio = kTrainSteps / (num_inference_steps + 1);
            for (int i = 0; i < num_inference_steps; ++i) {
                timesteps_.push_back((num_inference_steps - i) * step_ratio);
            }
        }

        sigmas_.reserve(static_cast<size_t>(num_inference_steps + 1));
        for (int i = 0; i < num_inference_steps; ++i) {
            sigmas_.push_back(interpolateSigma(static_cast<float>(timesteps_[static_cast<size_t>(i)]), base_sigmas_));
        }
        sigmas_.push_back(0.0f);
        model_outputs_.assign(static_cast<size_t>(kSolverOrder), {});
    }

    int numTimesteps() const override { return static_cast<int>(timesteps_.size()); }

    int timestepAt(int step_index) const override { return timesteps_.at(static_cast<size_t>(step_index)); }

    float sigmaAt(int step_index) const override { return sigmas_.at(static_cast<size_t>(step_index)); }

    float sigmaNextAt(int step_index) const override { return sigmas_.at(static_cast<size_t>(step_index + 1)); }

    std::vector<float> addNoise(
        const std::vector<float>& original,
        const std::vector<float>& noise,
        float denoise_strength) const override {
        if (original.size() != noise.size()) {
            throw std::invalid_argument("addNoise size mismatch");
        }
        const int t = static_cast<int>(denoise_strength * static_cast<float>(kTrainSteps));
        const float sigma = interpolateSigma(static_cast<float>(std::clamp(t, 0, kTrainSteps - 1)), base_sigmas_);
        const auto [alpha_t, sigma_t] = sigmaToAlphaSigma(sigma);
        std::vector<float> out(original.size());
        for (size_t i = 0; i < out.size(); ++i) {
            out[i] = alpha_t * original[i] + sigma_t * noise[i];
        }
        return out;
    }

    std::vector<float> step(
        const std::vector<float>& sample,
        const std::vector<float>& model_output,
        int step_index) override {
        if (step_index < 0 || step_index + 1 >= static_cast<int>(sigmas_.size())) {
            throw std::out_of_range("DPM step index out of range");
        }

        const float sigma = sigmas_[static_cast<size_t>(step_index)];
        const std::vector<float> converted = convertModelOutput(model_output, sample, sigma);

        for (int i = 0; i < kSolverOrder - 1; ++i) {
            model_outputs_[static_cast<size_t>(i)] = std::move(model_outputs_[static_cast<size_t>(i + 1)]);
        }
        model_outputs_.back() = converted;

        const bool is_last_step = step_index == numTimesteps() - 1;
        const bool lower_order_second =
            step_index == numTimesteps() - 2 && timesteps_.size() < 15;

        std::vector<float> prev_sample;
        if (kSolverOrder == 1 || lower_order_nums_ < 1 || is_last_step) {
            prev_sample = firstOrderUpdate(model_outputs_.back(), sample, step_index);
        } else if (kSolverOrder == 2 || lower_order_nums_ < 2 || lower_order_second) {
            prev_sample = secondOrderUpdate(sample, step_index);
        } else {
            prev_sample = firstOrderUpdate(model_outputs_.back(), sample, step_index);
        }

        if (lower_order_nums_ < kSolverOrder) {
            ++lower_order_nums_;
        }
        return prev_sample;
    }

 private:
    static constexpr int kSolverOrder = 2;

    std::vector<float> convertModelOutput(
        const std::vector<float>& model_output,
        const std::vector<float>& sample,
        float sigma) const {
        const auto [alpha_t, sigma_t] = sigmaToAlphaSigma(sigma);
        std::vector<float> converted(model_output.size());
        for (size_t i = 0; i < converted.size(); ++i) {
            converted[i] = (sample[i] - sigma_t * model_output[i]) / alpha_t;
        }
        return converted;
    }

    std::vector<float> firstOrderUpdate(
        const std::vector<float>& converted_output,
        const std::vector<float>& sample,
        int step_index) const {
        const float sigma_next = sigmas_[static_cast<size_t>(step_index + 1)];
        const float sigma_curr = sigmas_[static_cast<size_t>(step_index)];
        const auto [alpha_t, sigma_t_val] = sigmaToAlphaSigma(sigma_next);
        const auto [alpha_s, sigma_s_val] = sigmaToAlphaSigma(sigma_curr);

        const float lambda_t = std::log(alpha_t) - std::log(sigma_t_val);
        const float lambda_s = std::log(alpha_s) - std::log(sigma_s_val);
        const float h = lambda_t - lambda_s;

        std::vector<float> out(sample.size());
        for (size_t i = 0; i < out.size(); ++i) {
            out[i] = (sigma_t_val / sigma_s_val) * sample[i] -
                     alpha_t * (std::exp(-h) - 1.0f) * converted_output[i];
        }
        return out;
    }

    std::vector<float> secondOrderUpdate(const std::vector<float>& sample, int step_index) const {
        const float sigma_next = sigmas_[static_cast<size_t>(step_index + 1)];
        const float sigma_s0 = sigmas_[static_cast<size_t>(step_index)];
        const float sigma_s1 = sigmas_[static_cast<size_t>(step_index - 1)];

        const auto [alpha_t, sigma_t_val] = sigmaToAlphaSigma(sigma_next);
        const auto [alpha_s0, sigma_s0_val] = sigmaToAlphaSigma(sigma_s0);
        const auto [alpha_s1, sigma_s1_val] = sigmaToAlphaSigma(sigma_s1);

        const float lambda_t = std::log(alpha_t) - std::log(sigma_t_val);
        const float lambda_s0 = std::log(alpha_s0) - std::log(sigma_s0_val);
        const float lambda_s1 = std::log(alpha_s1) - std::log(sigma_s1_val);

        const std::vector<float>& m0 = model_outputs_.back();
        const std::vector<float>& m1 = model_outputs_[model_outputs_.size() - 2];

        const float h = lambda_t - lambda_s0;
        const float h_0 = lambda_s0 - lambda_s1;
        const float r0 = h_0 / h;

        std::vector<float> out(sample.size());
        for (size_t i = 0; i < out.size(); ++i) {
            const float d0 = m0[i];
            const float d1 = (1.0f / r0) * (m0[i] - m1[i]);
            out[i] = (sigma_t_val / sigma_s0_val) * sample[i] -
                     (alpha_t * (std::exp(-h) - 1.0f)) * d0 -
                     0.5f * (alpha_t * (std::exp(-h) - 1.0f)) * d1;
        }
        return out;
    }

    bool sdxl_;
    std::vector<float> alphas_cumprod_;
    std::vector<float> base_sigmas_;
    std::vector<int> timesteps_;
    std::vector<float> sigmas_;
    std::vector<std::vector<float>> model_outputs_;
    int lower_order_nums_ = 0;
};

}  // namespace

Scheduler* createDpmScheduler(bool sdxl) { return new DpmMultistepScheduler(sdxl); }

}  // namespace enclave::diffusion
