#include "enclave_qnn_app.h"

#include <algorithm>

namespace enclave::diffusion {

bool EnclaveQnnApp::initializeFromBinary(
    const std::string& bin_path,
    const std::string& backend_path,
    const std::string& system_path,
    const std::string& dsp_library_path,
    const std::string& htp_stub_library,
    std::string& error) {
    htp_stub_library_ = htp_stub_library;
    return createQnnModelFromBinary(
        bin_path,
        backend_path,
        system_path,
        dsp_library_path,
        htp_stub_library_.c_str(),
        model_,
        error);
}

void EnclaveQnnApp::enablePerformanceMode(std::string& error) {
    (void)error;
}

void EnclaveQnnApp::setSpatialSize(int output_width, int output_height) {
    output_width_ = std::max(output_width, 8);
    output_height_ = std::max(output_height, 8);
}

bool EnclaveQnnApp::executeSd15Unet(
    const std::vector<float>& latents,
    int timestep,
    const std::vector<float>& text_embedding,
    std::vector<float>& noise_pred,
    std::string& error) {
    if (model_ == nullptr) {
        error = "QNN model is not loaded.";
        return false;
    }
    const int sample_width = std::max(output_width_ / 8, 1);
    const int sample_height = std::max(output_height_ / 8, 1);
    const int latent_count = 4 * sample_width * sample_height;
    if (static_cast<int>(latents.size()) < latent_count) {
        error = "UNet latent buffer is too small.";
        return false;
    }
    noise_pred.resize(static_cast<size_t>(latent_count));
    return model_->executeSd15Unet(
        latents.data(),
        timestep,
        text_embedding.data(),
        static_cast<int>(text_embedding.size() / 77),
        sample_width,
        sample_height,
        noise_pred.data(),
        error);
}

bool EnclaveQnnApp::executeSdxlUnet(
    const std::vector<float>& latents,
    const std::vector<float>& encoder_hidden_states,
    int timestep,
    const std::vector<float>& time_ids,
    const std::vector<float>& text_embeds,
    std::vector<float>& noise_pred,
    std::string& error) {
    if (model_ == nullptr) {
        error = "QNN model is not loaded.";
        return false;
    }
    const int sample_width = std::max(output_width_ / 8, 1);
    const int sample_height = std::max(output_height_ / 8, 1);
    const int latent_count = 4 * sample_width * sample_height;
    if (static_cast<int>(latents.size()) < latent_count) {
        error = "UNet latent buffer is too small.";
        return false;
    }
    noise_pred.resize(static_cast<size_t>(latent_count));
    return model_->executeSdxlUnet(
        latents.data(),
        timestep,
        encoder_hidden_states.data(),
        text_embeds.data(),
        time_ids.data(),
        sample_width,
        sample_height,
        noise_pred.data(),
        error);
}

bool EnclaveQnnApp::executeSd15VaeEncoder(
    const std::vector<float>& pixel_values,
    std::vector<float>& mean,
    std::vector<float>& std_dev,
    std::string& error) {
    if (model_ == nullptr) {
        error = "QNN model is not loaded.";
        return false;
    }
    const int latent_count = 4 * std::max(output_width_ / 8, 1) * std::max(output_height_ / 8, 1);
    mean.resize(static_cast<size_t>(latent_count));
    std_dev.resize(static_cast<size_t>(latent_count));
    return model_->executeSd15VaeEncoder(
        pixel_values.data(),
        output_width_,
        output_height_,
        mean.data(),
        std_dev.data(),
        error);
}

bool EnclaveQnnApp::executeSd15VaeDecoder(
    const std::vector<float>& latents,
    std::vector<float>& pixel_values,
    std::string& error) {
    if (model_ == nullptr) {
        error = "QNN model is not loaded.";
        return false;
    }
    const int sample_width = std::max(output_width_ / 8, 1);
    const int sample_height = std::max(output_height_ / 8, 1);
    const int pixel_count = 3 * output_width_ * output_height_;
    pixel_values.resize(static_cast<size_t>(pixel_count));
    return model_->executeSd15VaeDecoder(
        latents.data(),
        sample_width,
        sample_height,
        output_width_,
        output_height_,
        pixel_values.data(),
        error);
}

bool EnclaveQnnApp::executeSdxlVaeEncoder(
    const std::vector<float>& pixel_values,
    std::vector<float>& mean,
    std::vector<float>& std_dev,
    std::string& error) {
    if (model_ == nullptr) {
        error = "QNN model is not loaded.";
        return false;
    }
    const int latent_count = 4 * std::max(output_width_ / 8, 1) * std::max(output_height_ / 8, 1);
    mean.resize(static_cast<size_t>(latent_count));
    std_dev.resize(static_cast<size_t>(latent_count));
    return model_->executeSdxlVaeEncoder(
        pixel_values.data(),
        output_width_,
        output_height_,
        mean.data(),
        std_dev.data(),
        error);
}

bool EnclaveQnnApp::executeSdxlVaeDecoder(
    const std::vector<float>& latents,
    std::vector<float>& pixel_values,
    std::string& error) {
    if (model_ == nullptr) {
        error = "QNN model is not loaded.";
        return false;
    }
    const int sample_width = std::max(output_width_ / 8, 1);
    const int sample_height = std::max(output_height_ / 8, 1);
    const int pixel_count = 3 * output_width_ * output_height_;
    pixel_values.resize(static_cast<size_t>(pixel_count));
    return model_->executeSdxlVaeDecoder(
        latents.data(),
        sample_width,
        sample_height,
        output_width_,
        output_height_,
        pixel_values.data(),
        error);
}

}  // namespace enclave::diffusion
