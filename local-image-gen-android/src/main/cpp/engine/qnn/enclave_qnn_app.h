#pragma once

#include "enclave_qnn_model.h"

#include <memory>
#include <string>
#include <vector>

namespace enclave::diffusion {

class EnclaveQnnSharedRuntime;

/** Vector-friendly facade over EnclaveQnnModel for graph runners. */
class EnclaveQnnApp {
 public:
    EnclaveQnnApp() = default;
    ~EnclaveQnnApp() = default;

    bool initializeFromBinary(
        const std::string& bin_path,
        EnclaveQnnSharedRuntime& runtime,
        std::string& error);

    bool initializeFromBinary(
        const std::string& bin_path,
        const std::string& backend_path,
        const std::string& system_path,
        const std::string& dsp_library_path,
        const std::string& htp_stub_library,
        std::string& error);

    void enablePerformanceMode(std::string& error);
    void setSpatialSize(int output_width, int output_height);

    bool executeSd15Unet(
        const std::vector<float>& latents,
        int timestep,
        const std::vector<float>& text_embedding,
        std::vector<float>& noise_pred,
        std::string& error);

    bool executeSdxlUnet(
        const std::vector<float>& latents,
        const std::vector<float>& encoder_hidden_states,
        int timestep,
        const std::vector<float>& time_ids,
        const std::vector<float>& text_embeds,
        std::vector<float>& noise_pred,
        std::string& error);

    bool executeSd15VaeEncoder(
        const std::vector<float>& pixel_values,
        std::vector<float>& mean,
        std::vector<float>& std_dev,
        std::string& error);

    bool executeSd15VaeDecoder(
        const std::vector<float>& latents,
        std::vector<float>& pixel_values,
        std::string& error);

    bool executeSdxlVaeEncoder(
        const std::vector<float>& pixel_values,
        std::vector<float>& mean,
        std::vector<float>& std_dev,
        std::string& error);

    bool executeSdxlVaeDecoder(
        const std::vector<float>& latents,
        std::vector<float>& pixel_values,
        std::string& error);

 private:
    std::unique_ptr<EnclaveQnnModel> model_;
    std::string htp_stub_library_;
    int output_width_ = 512;
    int output_height_ = 512;
};

}  // namespace enclave::diffusion
