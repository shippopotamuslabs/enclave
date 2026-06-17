#include "enclave_qnn_model.h"

#include "enclave_qnn_shared_runtime.h"
#include "HTP/QnnHtpDevice.h"
#include "qnn_graph_info.h"
#include "qnn_htp_device.h"
#include "qnn_log.h"
#include "qnn_runtime.h"
#include "qnn_tensor_access.h"
#include "qnn_tensor_io.h"

#include <dlfcn.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <cstring>
#include <fstream>
#include <vector>

namespace enclave::diffusion {
namespace {

void* inputClientData(Qnn_Tensor_t* inputs, uint32_t index) {
    if (inputs == nullptr || !qnn_tensor_access::isSupportedVersion(inputs[index].version)) {
        return nullptr;
    }
    return qnn_tensor_access::clientData(inputs[index]);
}

class MappedBinaryFile {
 public:
    ~MappedBinaryFile() { release(); }

    bool open(const std::string& path, std::string& error) {
        release();
        fd_ = ::open(path.c_str(), O_RDONLY);
        if (fd_ < 0) {
            error = "Failed to open QNN binary: " + path;
            return false;
        }
        struct stat st {};
        if (fstat(fd_, &st) != 0 || st.st_size <= 0) {
            error = "QNN binary is empty: " + path;
            return false;
        }
        size_ = static_cast<size_t>(st.st_size);
        data_ = mmap(nullptr, size_, PROT_READ, MAP_PRIVATE, fd_, 0);
        if (data_ == MAP_FAILED) {
            error = "Failed to mmap QNN binary: " + path;
            data_ = nullptr;
            size_ = 0;
            return false;
        }
        return true;
    }

    void release() {
        if (data_ != nullptr && size_ > 0) {
            munmap(data_, size_);
            data_ = nullptr;
            size_ = 0;
        }
        if (fd_ >= 0) {
            close(fd_);
            fd_ = -1;
        }
    }

    void* data() const {
        return data_;
    }

    size_t size() const { return size_; }

 private:
    void* data_ = nullptr;
    size_t size_ = 0;
    int fd_ = -1;
};

}  // namespace

struct EnclaveQnnModel::Impl {
    EnclaveQnnApis apis {};
    void* backend_handle = nullptr;
    void* system_handle = nullptr;
    Qnn_BackendHandle_t backend = nullptr;
    Qnn_DeviceHandle_t device = nullptr;
    Qnn_ContextHandle_t context = nullptr;
    EnclaveGraphInfoPtr* graphs_info = nullptr;
    uint32_t graphs_count = 0;
    Qnn_Tensor_t* inputs = nullptr;
    Qnn_Tensor_t* outputs = nullptr;
    bool initialized = false;
    bool owns_backend = true;
    EnclaveQnnSharedRuntime* shared_runtime = nullptr;
    std::string binary_path;
    QnnHtpDevice_Arch_t htp_arch = QNN_HTP_DEVICE_ARCH_NONE;
};

EnclaveQnnModel::~EnclaveQnnModel() { shutdown(); }

void EnclaveQnnModel::shutdown() {
    if (impl_ == nullptr) {
        return;
    }
    if (impl_->inputs != nullptr || impl_->outputs != nullptr) {
        uint32_t num_inputs = 0;
        uint32_t num_outputs = 0;
        if (impl_->graphs_info != nullptr && impl_->graphs_count > 0) {
            num_inputs = impl_->graphs_info[0]->num_input_tensors;
            num_outputs = impl_->graphs_info[0]->num_output_tensors;
        }
        tearDownExecutionTensors(
            impl_->inputs,
            impl_->outputs,
            num_inputs,
            num_outputs);
        impl_->inputs = nullptr;
        impl_->outputs = nullptr;
    }
    if (impl_->context != nullptr && impl_->apis.core.contextFree != nullptr) {
        impl_->apis.core.contextFree(impl_->context, nullptr);
        impl_->context = nullptr;
    }
    if (impl_->graphs_info != nullptr) {
        freeEnclaveGraphsInfo(&impl_->graphs_info, impl_->graphs_count);
        impl_->graphs_count = 0;
    }
    if (impl_->shared_runtime != nullptr) {
        EnclaveQnnSharedRuntime::dspCooldown();
        impl_->shared_runtime = nullptr;
        impl_->backend = nullptr;
        impl_->device = nullptr;
        impl_->apis = {};
    } else if (impl_->owns_backend) {
        if (impl_->device != nullptr && impl_->apis.core.deviceFree != nullptr) {
            impl_->apis.core.deviceFree(impl_->device);
            impl_->device = nullptr;
        }
        if (impl_->backend != nullptr && impl_->apis.core.backendFree != nullptr) {
            impl_->apis.core.backendFree(impl_->backend);
            impl_->backend = nullptr;
        }
        if (impl_->backend_handle != nullptr) {
            dlclose(impl_->backend_handle);
            impl_->backend_handle = nullptr;
        }
        if (impl_->system_handle != nullptr) {
            releaseEnclaveQnnSystem(impl_->system_handle);
            impl_->system_handle = nullptr;
        }
        impl_->apis = {};
    }
    impl_->initialized = false;
}

bool EnclaveQnnModel::loadContextFromBinary(const std::string& binary_path, std::string& error) {
    if (impl_ == nullptr) {
        error = "QNN model is not configured.";
        return false;
    }
    if (impl_->apis.system.systemContextCreate == nullptr ||
        impl_->apis.system.systemContextGetBinaryInfo == nullptr ||
        impl_->apis.system.systemContextFree == nullptr) {
        error = "QNN system interface is incomplete.";
        return false;
    }

    MappedBinaryFile mapped;
    if (!mapped.open(binary_path, error)) {
        return false;
    }

    QnnSystemContext_Handle_t system_context = nullptr;
    if (impl_->apis.system.systemContextCreate(&system_context) != QNN_SUCCESS) {
        error = "systemContextCreate failed.";
        return false;
    }

    const QnnSystemContext_BinaryInfo_t* binary_info = nullptr;
    Qnn_ContextBinarySize_t binary_info_size = 0;
    if (impl_->apis.system.systemContextGetBinaryInfo(
            system_context,
            mapped.data(),
            mapped.size(),
            &binary_info,
            &binary_info_size) != QNN_SUCCESS) {
        impl_->apis.system.systemContextFree(system_context);
        error = "systemContextGetBinaryInfo failed.";
        return false;
    }

    if (!copyBinaryMetadataToGraphs(binary_info, &impl_->graphs_info, impl_->graphs_count)) {
        impl_->apis.system.systemContextFree(system_context);
        error = "Failed to parse QNN graph metadata.";
        return false;
    }
    impl_->apis.system.systemContextFree(system_context);

    if (impl_->owns_backend) {
        if (impl_->apis.core.backendCreate == nullptr) {
            error = "backendCreate is unavailable.";
            return false;
        }
        if (impl_->apis.core.backendCreate(nullptr, nullptr, &impl_->backend) != QNN_BACKEND_NO_ERROR) {
            error = "backendCreate failed.";
            return false;
        }

        createHtpDevice(impl_->apis, impl_->htp_arch, impl_->device);
    }

    if (impl_->apis.core.contextCreateFromBinary == nullptr) {
        error = "contextCreateFromBinary is unavailable.";
        return false;
    }
    ENCLAVE_QNN_LOGI(
        "Loading QNN context from %s (%zu bytes)%s.",
        binary_path.c_str(),
        mapped.size(),
        impl_->shared_runtime != nullptr ? " [shared runtime]" : "");
    if (impl_->apis.core.contextCreateFromBinary(
            impl_->backend,
            impl_->device,
            nullptr,
            mapped.data(),
            mapped.size(),
            &impl_->context,
            nullptr) != QNN_CONTEXT_NO_ERROR) {
        error = "contextCreateFromBinary failed.";
        return false;
    }
    mapped.release();

    for (uint32_t graph_idx = 0; graph_idx < impl_->graphs_count; ++graph_idx) {
        if (impl_->apis.core.graphRetrieve == nullptr) {
            error = "graphRetrieve is unavailable.";
            return false;
        }
        auto* graph = impl_->graphs_info[graph_idx];
        if (impl_->apis.core.graphRetrieve(
                impl_->context,
                graph->graph_name,
                &graph->graph) != QNN_SUCCESS) {
            error = "graphRetrieve failed.";
            return false;
        }
    }
    return true;
}

bool EnclaveQnnModel::initializeGraph(std::string& error) {
    if (impl_ == nullptr) {
        error = "QNN model is not configured.";
        return false;
    }
    if (impl_->initialized) {
        return true;
    }
    if (!loadContextFromBinary(impl_->binary_path, error)) {
        return false;
    }
    impl_->initialized = true;
    return true;
}

bool EnclaveQnnModel::ensureIoTensors(std::string& error) {
    if (impl_ == nullptr || impl_->graphs_info == nullptr || impl_->graphs_count == 0) {
        error = "QNN graph metadata is missing.";
        return false;
    }
    if (impl_->inputs != nullptr && impl_->outputs != nullptr) {
        return true;
    }
    return setupExecutionTensors(
        &impl_->inputs,
        &impl_->outputs,
        *impl_->graphs_info[0],
        error);
}

bool EnclaveQnnModel::runGraph(std::string& error) {
    if (!ensureIoTensors(error)) {
        return false;
    }
    const auto& graph = *impl_->graphs_info[0];
    if (impl_->apis.core.graphExecute == nullptr) {
        error = "graphExecute is unavailable.";
        return false;
    }
    if (impl_->apis.core.graphExecute(
            graph.graph,
            impl_->inputs,
            graph.num_input_tensors,
            impl_->outputs,
            graph.num_output_tensors,
            nullptr,
            nullptr) != QNN_GRAPH_NO_ERROR) {
        error = "QNN graphExecute failed.";
        return false;
    }
    return true;
}

bool EnclaveQnnModel::executeSd15Unet(
    const float* latents,
    int timestep,
    const float* text_embedding,
    int text_embedding_size,
    int sample_width,
    int sample_height,
    float* noise_pred,
    std::string& error) {
    if (!initializeGraph(error)) {
        return false;
    }
    if (!ensureIoTensors(error)) {
        return false;
    }
    const auto& graph = *impl_->graphs_info[0];
    if (graph.num_input_tensors < 3 || graph.num_output_tensors < 1) {
        error = "SD15 UNet graph has unexpected tensor count.";
        return false;
    }
    const int latent_count = 4 * sample_width * sample_height;
    if (inputClientData(impl_->inputs, 0) == nullptr ||
        inputClientData(impl_->inputs, 1) == nullptr ||
        inputClientData(impl_->inputs, 2) == nullptr) {
        error = "SD15 UNet input tensors are invalid.";
        return false;
    }
    if (!writeFloatValuesToTensor(
            impl_->inputs[0], latents, static_cast<size_t>(latent_count), error) ||
        !writeFloatValuesToTensor(
            impl_->inputs[2],
            text_embedding,
            static_cast<size_t>(77 * text_embedding_size),
            error)) {
        return false;
    }
    reinterpret_cast<int32_t*>(inputClientData(impl_->inputs, 1))[0] = timestep;

    if (!runGraph(error)) {
        return false;
    }
    return convertTensorOutputToFloatBuffer(
        impl_->outputs[0],
        noise_pred,
        static_cast<size_t>(latent_count),
        error);
}

bool EnclaveQnnModel::executeSdxlUnet(
    const float* latents,
    int timestep,
    const float* encoder_hidden_states,
    const float* text_embeds,
    const float* time_ids,
    int sample_width,
    int sample_height,
    float* noise_pred,
    std::string& error) {
    if (!initializeGraph(error)) {
        return false;
    }
    if (!ensureIoTensors(error)) {
        return false;
    }
    const auto& graph = *impl_->graphs_info[0];
    if (graph.num_input_tensors < 5 || graph.num_output_tensors < 1) {
        error = "SDXL UNet graph has unexpected tensor count.";
        return false;
    }
    const int latent_count = 4 * sample_width * sample_height;
    if (inputClientData(impl_->inputs, 0) == nullptr ||
        inputClientData(impl_->inputs, 1) == nullptr ||
        inputClientData(impl_->inputs, 2) == nullptr ||
        inputClientData(impl_->inputs, 3) == nullptr ||
        inputClientData(impl_->inputs, 4) == nullptr) {
        error = "SDXL UNet input tensors are invalid.";
        return false;
    }
    if (!writeFloatValuesToTensor(
            impl_->inputs[0], latents, static_cast<size_t>(latent_count), error) ||
        !writeFloatValuesToTensor(
            impl_->inputs[1],
            encoder_hidden_states,
            static_cast<size_t>(77 * 2048),
            error) ||
        !writeFloatValuesToTensor(impl_->inputs[3], time_ids, 6, error) ||
        !writeFloatValuesToTensor(impl_->inputs[4], text_embeds, 1280, error)) {
        return false;
    }
    reinterpret_cast<int32_t*>(inputClientData(impl_->inputs, 2))[0] = timestep;

    if (!runGraph(error)) {
        return false;
    }
    return convertTensorOutputToFloatBuffer(
        impl_->outputs[0],
        noise_pred,
        static_cast<size_t>(latent_count),
        error);
}

bool EnclaveQnnModel::executeSd15VaeEncoder(
    const float* pixel_values,
    int width,
    int height,
    float* mean,
    float* std_dev,
    std::string& error) {
    if (!initializeGraph(error)) {
        return false;
    }
    if (!ensureIoTensors(error)) {
        return false;
    }
    const auto& graph = *impl_->graphs_info[0];
    if (graph.num_input_tensors < 1 || graph.num_output_tensors < 2) {
        error = "VAE encoder graph has unexpected tensor count.";
        return false;
    }
    const int pixel_count = 3 * width * height;
    if (inputClientData(impl_->inputs, 0) == nullptr) {
        error = "VAE encoder input tensor is invalid.";
        return false;
    }
    if (!writeFloatValuesToTensor(
            impl_->inputs[0],
            pixel_values,
            static_cast<size_t>(pixel_count),
            error)) {
        return false;
    }
    if (!runGraph(error)) {
        return false;
    }
    const int latent_count = 4 * (width / 8) * (height / 8);
    const size_t latent_elements = static_cast<size_t>(latent_count);
    return convertTensorOutputToFloatBuffer(impl_->outputs[0], mean, latent_elements, error) &&
           convertTensorOutputToFloatBuffer(impl_->outputs[1], std_dev, latent_elements, error);
}

bool EnclaveQnnModel::executeSd15VaeDecoder(
    const float* latents,
    int sample_width,
    int sample_height,
    int output_width,
    int output_height,
    float* pixel_values,
    std::string& error) {
    if (!initializeGraph(error)) {
        return false;
    }
    if (!ensureIoTensors(error)) {
        return false;
    }
    const auto& graph = *impl_->graphs_info[0];
    if (graph.num_input_tensors < 1 || graph.num_output_tensors < 1) {
        error = "VAE decoder graph has unexpected tensor count.";
        return false;
    }
    const int latent_count = 4 * sample_width * sample_height;
    if (inputClientData(impl_->inputs, 0) == nullptr) {
        error = "VAE decoder input tensor is invalid.";
        return false;
    }
    if (!writeFloatValuesToTensor(
            impl_->inputs[0], latents, static_cast<size_t>(latent_count), error)) {
        return false;
    }
    if (!runGraph(error)) {
        return false;
    }
    const int pixel_count = 3 * output_width * output_height;
    return convertTensorOutputToFloatBuffer(
        impl_->outputs[0],
        pixel_values,
        static_cast<size_t>(pixel_count),
        error);
}

bool EnclaveQnnModel::executeSdxlVaeEncoder(
    const float* pixel_values,
    int width,
    int height,
    float* mean,
    float* std_dev,
    std::string& error) {
    return executeSd15VaeEncoder(pixel_values, width, height, mean, std_dev, error);
}

bool EnclaveQnnModel::executeSdxlVaeDecoder(
    const float* latents,
    int sample_width,
    int sample_height,
    int output_width,
    int output_height,
    float* pixel_values,
    std::string& error) {
    return executeSd15VaeDecoder(
        latents,
        sample_width,
        sample_height,
        output_width,
        output_height,
        pixel_values,
        error);
}

bool createQnnModelWithSharedRuntime(
    const std::string& bin_path,
    EnclaveQnnSharedRuntime& runtime,
    std::unique_ptr<EnclaveQnnModel>& out,
    std::string& error) {
    if (!runtime.isInitialized()) {
        error = "Shared QNN runtime is not initialized.";
        return false;
    }
    auto model = std::make_unique<EnclaveQnnModel>();
    model->impl_ = std::make_unique<EnclaveQnnModel::Impl>();
    model->impl_->binary_path = bin_path;
    model->impl_->shared_runtime = &runtime;
    model->impl_->owns_backend = false;
    model->impl_->apis = runtime.apis();
    model->impl_->backend = runtime.backend();
    model->impl_->device = runtime.device();
    if (!model->initializeGraph(error)) {
        model.reset();
        return false;
    }
    out = std::move(model);
    return true;
}

bool createQnnModelFromBinary(
    const std::string& bin_path,
    const std::string& backend_path,
    const std::string& system_path,
    const std::string& dsp_library_path,
    const char* htp_stub_library,
    std::unique_ptr<EnclaveQnnModel>& out,
    std::string& error) {
    setDspLibraryPath(dsp_library_path);
    if (!preloadQnnHtpLibraries(dsp_library_path, htp_stub_library, error)) {
        return false;
    }
    auto model = std::make_unique<EnclaveQnnModel>();
    model->impl_ = std::make_unique<EnclaveQnnModel::Impl>();
    model->impl_->binary_path = bin_path;
    model->impl_->htp_arch = htpArchFromStubLibrary(htp_stub_library);
    model->impl_->owns_backend = true;

    if (!acquireEnclaveQnnSystem(
            system_path,
            model->impl_->apis,
            &model->impl_->system_handle,
            error)) {
        return false;
    }
    if (!loadEnclaveQnnBackend(
            backend_path,
            model->impl_->apis,
            &model->impl_->backend_handle,
            error)) {
        releaseEnclaveQnnSystem(model->impl_->system_handle);
        model->impl_->system_handle = nullptr;
        return false;
    }
    if (!model->initializeGraph(error)) {
        model.reset();
        return false;
    }
    out = std::move(model);
    return true;
}

}  // namespace enclave::diffusion
