#include "qnn_tensor_io.h"

#include "qnn_log.h"
#include "qnn_tensor_access.h"

#include <cmath>
#include <cstdlib>
#include <cstring>
#include <numeric>

namespace enclave::diffusion {
namespace {

using namespace qnn_tensor_access;

size_t dataTypeSizeBytes(Qnn_DataType_t data_type) {
    switch (data_type) {
        case QNN_DATATYPE_INT_8:
        case QNN_DATATYPE_UINT_8:
        case QNN_DATATYPE_SFIXED_POINT_8:
        case QNN_DATATYPE_UFIXED_POINT_8:
        case QNN_DATATYPE_BOOL_8:
            return 1;
        case QNN_DATATYPE_INT_16:
        case QNN_DATATYPE_UINT_16:
        case QNN_DATATYPE_FLOAT_16:
        case QNN_DATATYPE_BFLOAT_16:
        case QNN_DATATYPE_SFIXED_POINT_16:
        case QNN_DATATYPE_UFIXED_POINT_16:
            return 2;
        case QNN_DATATYPE_INT_32:
        case QNN_DATATYPE_UINT_32:
        case QNN_DATATYPE_FLOAT_32:
        case QNN_DATATYPE_SFIXED_POINT_32:
        case QNN_DATATYPE_UFIXED_POINT_32:
            return 4;
        case QNN_DATATYPE_INT_64:
        case QNN_DATATYPE_UINT_64:
        case QNN_DATATYPE_FLOAT_64:
            return 8;
        default:
            return 0;
    }
}

bool allocateTensorBuffer(Qnn_Tensor_t& tensor, std::string& error) {
    const uint32_t tensor_rank = rank(tensor);
    const uint32_t* dims = dimensions(tensor);
    if (tensor_rank == 0 || dims == nullptr) {
        error = "Invalid tensor metadata for buffer allocation.";
        return false;
    }
    size_t element_count = 1;
    for (uint32_t i = 0; i < tensor_rank; ++i) {
        element_count *= dims[i];
    }
    const size_t type_size = dataTypeSizeBytes(dataType(tensor));
    if (type_size == 0) {
        error = "Unsupported QNN tensor data type.";
        return false;
    }
    const size_t byte_length = element_count * type_size;
    auto* buffer = static_cast<uint8_t*>(std::calloc(1, byte_length));
    if (buffer == nullptr) {
        error = "Failed to allocate QNN tensor buffer.";
        return false;
    }
    setClientBuffer(tensor, buffer, byte_length);
    return true;
}

bool copyExecutionTensorMetadata(Qnn_Tensor_t& dst, const Qnn_Tensor_t& src) {
    if (!isSupportedVersion(src.version)) {
        return false;
    }
    dst.version = src.version;
    if (src.version == QNN_TENSOR_VERSION_1) {
        dst.v1 = src.v1;
        dst.v1.name = nullptr;
        dst.v1.dimensions = nullptr;
        if (src.v1.rank > 0 && src.v1.dimensions != nullptr) {
            const size_t bytes = src.v1.rank * sizeof(uint32_t);
            dst.v1.dimensions = static_cast<uint32_t*>(std::malloc(bytes));
            if (dst.v1.dimensions == nullptr) {
                return false;
            }
            std::memcpy(dst.v1.dimensions, src.v1.dimensions, bytes);
        }
        return true;
    }

    dst.v2 = src.v2;
    dst.v2.name = nullptr;
    dst.v2.dimensions = nullptr;
    dst.v2.isDynamicDimensions = nullptr;
    if (src.v2.rank > 0 && src.v2.dimensions != nullptr) {
        const size_t dim_bytes = src.v2.rank * sizeof(uint32_t);
        dst.v2.dimensions = static_cast<uint32_t*>(std::malloc(dim_bytes));
        if (dst.v2.dimensions == nullptr) {
            return false;
        }
        std::memcpy(dst.v2.dimensions, src.v2.dimensions, dim_bytes);
        if (src.v2.isDynamicDimensions != nullptr) {
            const size_t dynamic_bytes = src.v2.rank * sizeof(uint8_t);
            dst.v2.isDynamicDimensions = static_cast<uint8_t*>(std::malloc(dynamic_bytes));
            if (dst.v2.isDynamicDimensions == nullptr) {
                std::free(dst.v2.dimensions);
                dst.v2.dimensions = nullptr;
                return false;
            }
            std::memcpy(dst.v2.isDynamicDimensions, src.v2.isDynamicDimensions, dynamic_bytes);
        }
    }
    return true;
}

bool setupTensorArray(
    Qnn_Tensor_t** tensors,
    uint32_t count,
    Qnn_Tensor_t* wrappers,
    std::string& error) {
    if (count == 0) {
        *tensors = nullptr;
        return true;
    }
    if (wrappers == nullptr) {
        error = "Missing QNN tensor metadata.";
        return false;
    }
    *tensors = static_cast<Qnn_Tensor_t*>(std::calloc(count, sizeof(Qnn_Tensor_t)));
    if (*tensors == nullptr) {
        error = "Failed to allocate execution tensor array.";
        return false;
    }
    for (uint32_t i = 0; i < count; ++i) {
        (*tensors)[i] = QNN_TENSOR_INIT;
        if (!copyExecutionTensorMetadata((*tensors)[i], wrappers[i])) {
            error = "Unsupported tensor version in execution setup.";
            tearDownExecutionTensors(*tensors, nullptr, i, 0);
            *tensors = nullptr;
            return false;
        }
        if (!allocateTensorBuffer((*tensors)[i], error)) {
            tearDownExecutionTensors(*tensors, nullptr, i + 1, 0);
            *tensors = nullptr;
            return false;
        }
    }
    return true;
}

void freeExecutionTensorArray(Qnn_Tensor_t* tensors, uint32_t count) {
    if (tensors == nullptr) {
        return;
    }
    for (uint32_t i = 0; i < count; ++i) {
        std::free(dimensions(tensors[i]));
        std::free(clientData(tensors[i]));
        if (tensors[i].version == QNN_TENSOR_VERSION_2) {
            std::free(tensors[i].v2.isDynamicDimensions);
            tensors[i].v2.isDynamicDimensions = nullptr;
        }
        if (tensors[i].version == QNN_TENSOR_VERSION_1) {
            tensors[i].v1.dimensions = nullptr;
            tensors[i].v1.clientBuf.data = nullptr;
        } else if (tensors[i].version == QNN_TENSOR_VERSION_2) {
            tensors[i].v2.dimensions = nullptr;
            tensors[i].v2.clientBuf.data = nullptr;
        }
    }
    std::free(tensors);
}

}  // namespace

size_t tensorElementCount(const Qnn_Tensor_t& tensor) {
    const uint32_t tensor_rank = rank(tensor);
    const uint32_t* dims = dimensions(tensor);
    if (tensor_rank == 0 || dims == nullptr) {
        return 0;
    }
    size_t count = 1;
    for (uint32_t i = 0; i < tensor_rank; ++i) {
        count *= dims[i];
    }
    return count;
}

size_t tensorByteLength(const Qnn_Tensor_t& tensor, bool& ok) {
    ok = false;
    const size_t type_size = dataTypeSizeBytes(dataType(tensor));
    if (type_size == 0) {
        return 0;
    }
    const size_t count = tensorElementCount(tensor);
    if (count == 0) {
        return 0;
    }
    ok = true;
    return count * type_size;
}

bool setupExecutionTensors(
    Qnn_Tensor_t** inputs,
    Qnn_Tensor_t** outputs,
    const EnclaveGraphInfo& graph_info,
    std::string& error) {
    if (!setupTensorArray(inputs, graph_info.num_input_tensors, graph_info.input_tensors, error)) {
        return false;
    }
    if (!setupTensorArray(
            outputs,
            graph_info.num_output_tensors,
            graph_info.output_tensors,
            error)) {
        tearDownExecutionTensors(
            *inputs,
            nullptr,
            graph_info.num_input_tensors,
            0);
        *inputs = nullptr;
        return false;
    }
    return true;
}

void tearDownExecutionTensors(
    Qnn_Tensor_t* inputs,
    Qnn_Tensor_t* outputs,
    uint32_t num_inputs,
    uint32_t num_outputs) {
    freeExecutionTensorArray(inputs, num_inputs);
    freeExecutionTensorArray(outputs, num_outputs);
}

void floatToUfixed16(
    uint16_t* out,
    const float* in,
    int32_t offset,
    float scale,
    size_t num_elements) {
    constexpr size_t bit_width = 16;
    const double true_bit_width_max = std::pow(2.0, bit_width) - 1.0;
    const double encoding_min = static_cast<double>(offset) * static_cast<double>(scale);
    const double encoding_max = (true_bit_width_max + static_cast<double>(offset)) * scale;
    const double encoding_range = encoding_max - encoding_min;

    for (size_t i = 0; i < num_elements; ++i) {
        int quantized =
            static_cast<int>(std::round(true_bit_width_max * (in[i] - encoding_min) / encoding_range));
        if (quantized < 0) {
            quantized = 0;
        } else if (quantized > static_cast<int>(true_bit_width_max)) {
            quantized = static_cast<int>(true_bit_width_max);
        }
        out[i] = static_cast<uint16_t>(quantized);
    }
}

void ufixed16ToFloat(
    float* out,
    const uint16_t* in,
    int32_t offset,
    float scale,
    size_t num_elements) {
    const double offset_double = static_cast<double>(offset);
    const double scale_double = static_cast<double>(scale);
    for (size_t i = 0; i < num_elements; ++i) {
        out[i] = static_cast<float>((static_cast<double>(in[i]) + offset_double) * scale_double);
    }
}

bool writeFloatValuesToTensor(
    Qnn_Tensor_t& tensor,
    const float* values,
    size_t element_count,
    std::string& error) {
    if (values == nullptr || element_count == 0) {
        error = "Tensor input values are empty.";
        return false;
    }
    void* buffer = clientData(tensor);
    if (buffer == nullptr) {
        error = "Tensor input buffer is missing.";
        return false;
    }
    const Qnn_DataType_t dtype = dataType(tensor);
    const auto& quant = quantParams(tensor);
    switch (dtype) {
        case QNN_DATATYPE_FLOAT_32:
            if (clientDataSize(tensor) < element_count * sizeof(float)) {
                error = "Float tensor input buffer is too small.";
                return false;
            }
            std::memcpy(buffer, values, element_count * sizeof(float));
            return true;
        case QNN_DATATYPE_UFIXED_POINT_16:
            if (clientDataSize(tensor) < element_count * sizeof(uint16_t)) {
                error = "Quantized tensor input buffer is too small.";
                return false;
            }
            floatToUfixed16(
                reinterpret_cast<uint16_t*>(buffer),
                values,
                quant.scaleOffsetEncoding.offset,
                quant.scaleOffsetEncoding.scale,
                element_count);
            return true;
        default:
            error = "Unsupported tensor input data type.";
            return false;
    }
}

bool convertTensorOutputToFloat(const Qnn_Tensor_t& tensor, float** out, std::string& error) {
    if (out == nullptr) {
        error = "Output pointer is null.";
        return false;
    }
    *out = nullptr;
    const void* buffer = clientData(tensor);
    if (buffer == nullptr) {
        error = "Tensor output buffer is missing.";
        return false;
    }
    const size_t element_count = tensorElementCount(tensor);
    if (element_count == 0) {
        error = "Tensor output has zero elements.";
        return false;
    }
    auto* float_buffer = static_cast<float*>(std::malloc(element_count * sizeof(float)));
    if (float_buffer == nullptr) {
        error = "Failed to allocate float conversion buffer.";
        return false;
    }

    const Qnn_DataType_t dtype = dataType(tensor);
    const auto& quant = quantParams(tensor);
    switch (dtype) {
        case QNN_DATATYPE_FLOAT_32:
            std::memcpy(float_buffer, buffer, element_count * sizeof(float));
            break;
        case QNN_DATATYPE_UFIXED_POINT_16:
            ufixed16ToFloat(
                float_buffer,
                reinterpret_cast<const uint16_t*>(buffer),
                quant.scaleOffsetEncoding.offset,
                quant.scaleOffsetEncoding.scale,
                element_count);
            break;
        case QNN_DATATYPE_UFIXED_POINT_8: {
            const double offset_double = quant.scaleOffsetEncoding.offset;
            const double scale_double = quant.scaleOffsetEncoding.scale;
            const auto* bytes = reinterpret_cast<const uint8_t*>(buffer);
            for (size_t i = 0; i < element_count; ++i) {
                float_buffer[i] = static_cast<float>(
                    (static_cast<double>(bytes[i]) + offset_double) * scale_double);
            }
            break;
        }
        default:
            std::free(float_buffer);
            error = "Unsupported tensor output data type.";
            return false;
    }
    *out = float_buffer;
    return true;
}

}  // namespace enclave::diffusion
