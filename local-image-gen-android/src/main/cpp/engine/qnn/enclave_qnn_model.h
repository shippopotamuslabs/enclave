#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace enclave::diffusion {

class EnclaveQnnSharedRuntime;

/** One serialized QNN context (UNet or VAE) using raw QNN C APIs only. */
class EnclaveQnnModel {
 public:
    EnclaveQnnModel() = default;
    ~EnclaveQnnModel();

    EnclaveQnnModel(const EnclaveQnnModel&) = delete;
    EnclaveQnnModel& operator=(const EnclaveQnnModel&) = delete;

    bool initializeGraph(std::string& error);
    void shutdown();

    bool executeSd15Unet(
        const float* latents,
        int timestep,
        const float* text_embedding,
        int text_embedding_size,
        int sample_width,
        int sample_height,
        float* noise_pred,
        std::string& error);

    bool executeSdxlUnet(
        const float* latents,
        int timestep,
        const float* encoder_hidden_states,
        const float* text_embeds,
        const float* time_ids,
        int sample_width,
        int sample_height,
        float* noise_pred,
        std::string& error);

    bool executeSd15VaeEncoder(
        const float* pixel_values,
        int width,
        int height,
        float* mean,
        float* std_dev,
        std::string& error);

    bool executeSd15VaeDecoder(
        const float* latents,
        int sample_width,
        int sample_height,
        int output_width,
        int output_height,
        float* pixel_values,
        std::string& error);

    bool executeSdxlVaeEncoder(
        const float* pixel_values,
        int width,
        int height,
        float* mean,
        float* std_dev,
        std::string& error);

    bool executeSdxlVaeDecoder(
        const float* latents,
        int sample_width,
        int sample_height,
        int output_width,
        int output_height,
        float* pixel_values,
        std::string& error);

 private:
    bool ensureIoTensors(std::string& error);
    bool runGraph(std::string& error);
    bool loadContextFromBinary(const std::string& binary_path, std::string& error);

    struct Impl;
    std::unique_ptr<Impl> impl_;

    friend bool createQnnModelFromBinary(
        const std::string& bin_path,
        const std::string& backend_path,
        const std::string& system_path,
        const std::string& dsp_library_path,
        const char* htp_stub_library,
        std::unique_ptr<EnclaveQnnModel>& out,
        std::string& error);

    friend bool createQnnModelWithSharedRuntime(
        const std::string& bin_path,
        EnclaveQnnSharedRuntime& runtime,
        std::unique_ptr<EnclaveQnnModel>& out,
        std::string& error);
};

bool createQnnModelWithSharedRuntime(
    const std::string& bin_path,
    EnclaveQnnSharedRuntime& runtime,
    std::unique_ptr<EnclaveQnnModel>& out,
    std::string& error);

bool createQnnModelFromBinary(
    const std::string& bin_path,
    const std::string& backend_path,
    const std::string& system_path,
    const std::string& dsp_library_path,
    const char* htp_stub_library,
    std::unique_ptr<EnclaveQnnModel>& out,
    std::string& error);

void setDspLibraryPath(const std::string& dsp_library_path);

}  // namespace enclave::diffusion
