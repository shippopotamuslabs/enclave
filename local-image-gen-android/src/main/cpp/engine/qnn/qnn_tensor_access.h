#pragma once

#include "QnnTensor.h"

#include <cstdint>
#include <cstring>

namespace enclave::diffusion {
namespace qnn_tensor_access {

inline bool isSupportedVersion(Qnn_TensorVersion_t version) {
    return version == QNN_TENSOR_VERSION_1 || version == QNN_TENSOR_VERSION_2;
}

inline uint32_t rank(const Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.rank;
    }
    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return tensor.v2.rank;
    }
    return 0;
}

inline uint32_t* dimensions(Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.dimensions;
    }
    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return tensor.v2.dimensions;
    }
    return nullptr;
}

inline const uint32_t* dimensions(const Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.dimensions;
    }
    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return tensor.v2.dimensions;
    }
    return nullptr;
}

inline Qnn_DataType_t dataType(const Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.dataType;
    }
    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return tensor.v2.dataType;
    }
    return QNN_DATATYPE_UNDEFINED;
}

inline Qnn_QuantizeParams_t& quantParams(Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.quantizeParams;
    }
    return tensor.v2.quantizeParams;
}

inline const Qnn_QuantizeParams_t& quantParams(const Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.quantizeParams;
    }
    return tensor.v2.quantizeParams;
}

inline void* clientData(Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.clientBuf.data;
    }
    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return tensor.v2.clientBuf.data;
    }
    return nullptr;
}

inline const void* clientData(const Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.clientBuf.data;
    }
    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return tensor.v2.clientBuf.data;
    }
    return nullptr;
}

inline uint64_t clientDataSize(const Qnn_Tensor_t& tensor) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        return tensor.v1.clientBuf.dataSize;
    }
    if (tensor.version == QNN_TENSOR_VERSION_2) {
        return tensor.v2.clientBuf.dataSize;
    }
    return 0;
}

inline void setClientBuffer(Qnn_Tensor_t& tensor, void* data, uint64_t data_size) {
    if (tensor.version == QNN_TENSOR_VERSION_1) {
        tensor.v1.memType = QNN_TENSORMEMTYPE_RAW;
        tensor.v1.clientBuf.data = data;
        tensor.v1.clientBuf.dataSize = data_size;
        return;
    }
    if (tensor.version == QNN_TENSOR_VERSION_2) {
        tensor.v2.memType = QNN_TENSORMEMTYPE_RAW;
        tensor.v2.clientBuf.data = data;
        tensor.v2.clientBuf.dataSize = data_size;
    }
}

inline void copyQuantizeParams(Qnn_QuantizeParams_t& dst, const Qnn_QuantizeParams_t& src) {
    dst = QNN_QUANTIZE_PARAMS_INIT;
    dst.encodingDefinition = src.encodingDefinition;
    dst.quantizationEncoding = src.quantizationEncoding;
    if (src.quantizationEncoding == QNN_QUANTIZATION_ENCODING_SCALE_OFFSET) {
        dst.scaleOffsetEncoding = src.scaleOffsetEncoding;
    } else if (src.quantizationEncoding == QNN_QUANTIZATION_ENCODING_AXIS_SCALE_OFFSET) {
        dst.axisScaleOffsetEncoding.axis = src.axisScaleOffsetEncoding.axis;
        dst.axisScaleOffsetEncoding.numScaleOffsets = src.axisScaleOffsetEncoding.numScaleOffsets;
        if (src.axisScaleOffsetEncoding.numScaleOffsets > 0 &&
            src.axisScaleOffsetEncoding.scaleOffset != nullptr) {
            const size_t bytes =
                src.axisScaleOffsetEncoding.numScaleOffsets * sizeof(Qnn_ScaleOffset_t);
            dst.axisScaleOffsetEncoding.scaleOffset =
                static_cast<Qnn_ScaleOffset_t*>(std::malloc(bytes));
            if (dst.axisScaleOffsetEncoding.scaleOffset != nullptr) {
                std::memcpy(dst.axisScaleOffsetEncoding.scaleOffset, src.axisScaleOffsetEncoding.scaleOffset, bytes);
            }
        }
    }
}

inline void freeQuantizeParams(Qnn_QuantizeParams_t& quant) {
    if (quant.quantizationEncoding == QNN_QUANTIZATION_ENCODING_AXIS_SCALE_OFFSET) {
        std::free(quant.axisScaleOffsetEncoding.scaleOffset);
        quant.axisScaleOffsetEncoding.scaleOffset = nullptr;
    }
}

}  // namespace qnn_tensor_access
}  // namespace enclave::diffusion
