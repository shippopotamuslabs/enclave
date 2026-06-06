#pragma once

#include "QnnContext.h"
#include "QnnGraph.h"
#include "QnnTensor.h"
#include "QnnTypes.h"
#include "System/QnnSystemContext.h"

#include <cstdint>
#include <string>

namespace enclave::diffusion {

struct EnclaveGraphInfo {
    Qnn_GraphHandle_t graph = nullptr;
    char* graph_name = nullptr;
    Qnn_Tensor_t* input_tensors = nullptr;
    uint32_t num_input_tensors = 0;
    Qnn_Tensor_t* output_tensors = nullptr;
    uint32_t num_output_tensors = 0;
};

using EnclaveGraphInfoPtr = EnclaveGraphInfo*;

bool copyBinaryMetadataToGraphs(
    const QnnSystemContext_BinaryInfo_t* binary_info,
    EnclaveGraphInfoPtr** graphs_info,
    uint32_t& graphs_count);

void freeEnclaveGraphsInfo(EnclaveGraphInfoPtr** graphs_info, uint32_t graphs_count);

}  // namespace enclave::diffusion
