#include "qnn_graph_info.h"

#include "qnn_log.h"
#include "qnn_tensor_access.h"

#include <cstdlib>
#include <cstring>

namespace enclave::diffusion {
namespace {

using namespace qnn_tensor_access;

char* duplicateCString(const char* value) {
    if (value == nullptr) {
        return nullptr;
    }
    const size_t length = std::strlen(value);
    auto* copy = static_cast<char*>(std::malloc(length + 1));
    if (copy != nullptr) {
        std::memcpy(copy, value, length + 1);
    }
    return copy;
}

bool copyDimensionsAndDynamic(
    uint32_t rank_value,
    const uint32_t* src_dimensions,
    const uint8_t* src_dynamic,
    uint32_t** dst_dimensions,
    uint8_t** dst_dynamic) {
    *dst_dimensions = nullptr;
    *dst_dynamic = nullptr;
    if (rank_value == 0) {
        return true;
    }
    if (src_dimensions == nullptr) {
        return false;
    }
    const size_t dim_bytes = rank_value * sizeof(uint32_t);
    *dst_dimensions = static_cast<uint32_t*>(std::malloc(dim_bytes));
    if (*dst_dimensions == nullptr) {
        return false;
    }
    std::memcpy(*dst_dimensions, src_dimensions, dim_bytes);
    if (src_dynamic != nullptr) {
        *dst_dynamic = static_cast<uint8_t*>(std::malloc(rank_value * sizeof(uint8_t)));
        if (*dst_dynamic == nullptr) {
            std::free(*dst_dimensions);
            *dst_dimensions = nullptr;
            return false;
        }
        std::memcpy(*dst_dynamic, src_dynamic, rank_value * sizeof(uint8_t));
    }
    return true;
}

bool deepCopyTensorInfo(Qnn_Tensor_t* dst, const Qnn_Tensor_t* src) {
    if (dst == nullptr || src == nullptr || !isSupportedVersion(src->version)) {
        if (src != nullptr) {
            ENCLAVE_QNN_LOGE("Unsupported tensor version %u", src->version);
        }
        return false;
    }

    dst->version = src->version;
    if (dst->version == QNN_TENSOR_VERSION_1) {
        auto& dst_v1 = dst->v1;
        const auto& src_v1 = src->v1;
        dst_v1 = QNN_TENSOR_V1_INIT;
        dst_v1.id = src_v1.id;
        dst_v1.type = src_v1.type;
        dst_v1.dataFormat = src_v1.dataFormat;
        dst_v1.dataType = src_v1.dataType;
        dst_v1.name = duplicateCString(src_v1.name);
        copyQuantizeParams(dst_v1.quantizeParams, src_v1.quantizeParams);
        dst_v1.rank = src_v1.rank;
        if (src_v1.rank == 0 || src_v1.dimensions == nullptr) {
            dst_v1.dimensions = nullptr;
            return true;
        }
        const size_t dim_bytes = src_v1.rank * sizeof(uint32_t);
        dst_v1.dimensions = static_cast<uint32_t*>(std::malloc(dim_bytes));
        if (dst_v1.dimensions == nullptr) {
            return false;
        }
        std::memcpy(dst_v1.dimensions, src_v1.dimensions, dim_bytes);
        return true;
    }

    auto& dst_v2 = dst->v2;
    const auto& src_v2 = src->v2;
    dst_v2 = QNN_TENSOR_V2_INIT;
    dst_v2.id = src_v2.id;
    dst_v2.type = src_v2.type;
    dst_v2.dataFormat = src_v2.dataFormat;
    dst_v2.dataType = src_v2.dataType;
    dst_v2.name = duplicateCString(src_v2.name);
    copyQuantizeParams(dst_v2.quantizeParams, src_v2.quantizeParams);
    dst_v2.rank = src_v2.rank;
    if (!copyDimensionsAndDynamic(
            src_v2.rank,
            src_v2.dimensions,
            src_v2.isDynamicDimensions,
            &dst_v2.dimensions,
            &dst_v2.isDynamicDimensions)) {
        return false;
    }
    dst_v2.sparseParams = src_v2.sparseParams;
    dst_v2.isProduced = src_v2.isProduced;
    return true;
}

void freeTensorInfo(Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        auto& v1 = tensor.v1;
        std::free(const_cast<char*>(v1.name));
        std::free(v1.dimensions);
        freeQuantizeParams(v1.quantizeParams);
        v1 = QNN_TENSOR_V1_INIT;
        return;
    }
    if (tensor.version == QNN_TENSOR_VERSION_2) {
        auto& v2 = tensor.v2;
        std::free(const_cast<char*>(v2.name));
        std::free(v2.dimensions);
        std::free(v2.isDynamicDimensions);
        freeQuantizeParams(v2.quantizeParams);
        v2 = QNN_TENSOR_V2_INIT;
    }
}

void freeTensorArray(Qnn_Tensor_t*& tensors, uint32_t count) {
    if (tensors == nullptr) {
        return;
    }
    for (uint32_t i = 0; i < count; ++i) {
        freeTensorInfo(tensors[i]);
    }
    std::free(tensors);
    tensors = nullptr;
}

bool copyTensorArray(const Qnn_Tensor_t* src, Qnn_Tensor_t*& dst, uint32_t count) {
    if (count == 0) {
        dst = nullptr;
        return true;
    }
    if (src == nullptr) {
        return false;
    }
    dst = static_cast<Qnn_Tensor_t*>(std::calloc(count, sizeof(Qnn_Tensor_t)));
    if (dst == nullptr) {
        return false;
    }
    for (uint32_t i = 0; i < count; ++i) {
        dst[i] = QNN_TENSOR_INIT;
        if (!deepCopyTensorInfo(&dst[i], &src[i])) {
            freeTensorArray(dst, count);
            dst = nullptr;
            return false;
        }
    }
    return true;
}

bool copyGraphInfoV1(const QnnSystemContext_GraphInfoV1_t& src, EnclaveGraphInfo& dst) {
    dst.graph_name = duplicateCString(src.graphName);
    if (!copyTensorArray(src.graphInputs, dst.input_tensors, src.numGraphInputs)) {
        return false;
    }
    dst.num_input_tensors = src.numGraphInputs;
    if (!copyTensorArray(src.graphOutputs, dst.output_tensors, src.numGraphOutputs)) {
        return false;
    }
    dst.num_output_tensors = src.numGraphOutputs;
    return true;
}

bool copyGraphInfoV3(const QnnSystemContext_GraphInfoV3_t& src, EnclaveGraphInfo& dst) {
    dst.graph_name = duplicateCString(src.graphName);
    if (!copyTensorArray(src.graphInputs, dst.input_tensors, src.numGraphInputs)) {
        return false;
    }
    dst.num_input_tensors = src.numGraphInputs;
    if (!copyTensorArray(src.graphOutputs, dst.output_tensors, src.numGraphOutputs)) {
        return false;
    }
    dst.num_output_tensors = src.numGraphOutputs;
    return true;
}

bool copyGraphArray(
    const QnnSystemContext_GraphInfo_t* graphs,
    uint32_t num_graphs,
    EnclaveGraphInfoPtr** graphs_info) {
    if (graphs == nullptr || num_graphs == 0) {
        return false;
    }
    auto** info_ptr_array =
        static_cast<EnclaveGraphInfoPtr*>(std::calloc(num_graphs, sizeof(EnclaveGraphInfoPtr)));
    auto* info_array = static_cast<EnclaveGraphInfo*>(std::calloc(num_graphs, sizeof(EnclaveGraphInfo)));
    if (info_ptr_array == nullptr || info_array == nullptr) {
        std::free(info_ptr_array);
        std::free(info_array);
        return false;
    }
    for (uint32_t i = 0; i < num_graphs; ++i) {
        // Link the entry before copying so a failed copy is still reachable by
        // freeEnclaveGraphsInfo (which also needs entry 0 to find the backing array).
        info_ptr_array[i] = &info_array[i];
        bool copied = false;
        if (graphs[i].version == QNN_SYSTEM_CONTEXT_GRAPH_INFO_VERSION_1) {
            copied = copyGraphInfoV1(graphs[i].graphInfoV1, info_array[i]);
        } else if (graphs[i].version == QNN_SYSTEM_CONTEXT_GRAPH_INFO_VERSION_3) {
            copied = copyGraphInfoV3(graphs[i].graphInfoV3, info_array[i]);
        }
        if (!copied) {
            freeEnclaveGraphsInfo(&info_ptr_array, i + 1);
            return false;
        }
    }
    *graphs_info = info_ptr_array;
    return true;
}

}  // namespace

bool copyBinaryMetadataToGraphs(
    const QnnSystemContext_BinaryInfo_t* binary_info,
    EnclaveGraphInfoPtr** graphs_info,
    uint32_t& graphs_count) {
    graphs_count = 0;
    if (binary_info == nullptr || graphs_info == nullptr) {
        return false;
    }

    const QnnSystemContext_GraphInfo_t* graphs = nullptr;
    uint32_t num_graphs = 0;
    if (binary_info->version == QNN_SYSTEM_CONTEXT_BINARY_INFO_VERSION_1) {
        graphs = binary_info->contextBinaryInfoV1.graphs;
        num_graphs = binary_info->contextBinaryInfoV1.numGraphs;
    } else if (binary_info->version == QNN_SYSTEM_CONTEXT_BINARY_INFO_VERSION_2) {
        graphs = binary_info->contextBinaryInfoV2.graphs;
        num_graphs = binary_info->contextBinaryInfoV2.numGraphs;
    } else if (binary_info->version == QNN_SYSTEM_CONTEXT_BINARY_INFO_VERSION_3) {
        graphs = binary_info->contextBinaryInfoV3.graphs;
        num_graphs = binary_info->contextBinaryInfoV3.numGraphs;
    } else {
        ENCLAVE_QNN_LOGE("Unsupported QNN binary metadata version.");
        return false;
    }

    if (graphs == nullptr || num_graphs == 0) {
        ENCLAVE_QNN_LOGE("QNN binary metadata contains no graphs.");
        return false;
    }
    if (!copyGraphArray(graphs, num_graphs, graphs_info)) {
        return false;
    }
    graphs_count = num_graphs;
    return true;
}

void freeEnclaveGraphsInfo(EnclaveGraphInfoPtr** graphs_info, uint32_t graphs_count) {
    if (graphs_info == nullptr || *graphs_info == nullptr || graphs_count == 0) {
        return;
    }
    EnclaveGraphInfo* backing = (*graphs_info)[0];
    for (uint32_t i = 0; i < graphs_count; ++i) {
        EnclaveGraphInfo* graph = (*graphs_info)[i];
        if (graph == nullptr) {
            continue;
        }
        std::free(graph->graph_name);
        freeTensorArray(graph->input_tensors, graph->num_input_tensors);
        freeTensorArray(graph->output_tensors, graph->num_output_tensors);
    }
    std::free(backing);
    std::free(*graphs_info);
    *graphs_info = nullptr;
}

}  // namespace enclave::diffusion
