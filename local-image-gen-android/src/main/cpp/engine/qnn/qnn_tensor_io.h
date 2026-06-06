#pragma once

#include "qnn_graph_info.h"

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace enclave::diffusion {

size_t tensorElementCount(const Qnn_Tensor_t& tensor);

size_t tensorByteLength(const Qnn_Tensor_t& tensor, bool& ok);

bool setupExecutionTensors(
    Qnn_Tensor_t** inputs,
    Qnn_Tensor_t** outputs,
    const EnclaveGraphInfo& graph_info,
    std::string& error);

void tearDownExecutionTensors(
    Qnn_Tensor_t* inputs,
    Qnn_Tensor_t* outputs,
    uint32_t num_inputs,
    uint32_t num_outputs);

void floatToUfixed16(
    uint16_t* out,
    const float* in,
    int32_t offset,
    float scale,
    size_t num_elements);

void ufixed16ToFloat(
    float* out,
    const uint16_t* in,
    int32_t offset,
    float scale,
    size_t num_elements);

bool convertTensorOutputToFloat(const Qnn_Tensor_t& tensor, float** out, std::string& error);

bool writeFloatValuesToTensor(
    Qnn_Tensor_t& tensor,
    const float* values,
    size_t element_count,
    std::string& error);

}  // namespace enclave::diffusion
