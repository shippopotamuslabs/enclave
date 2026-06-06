#include "clip_prompt_utils.h"

#include "fp16_utils.h"

#include <algorithm>
#include <fstream>

namespace enclave::diffusion {
namespace {

bool readBinary(const std::string& path, std::vector<uint8_t>& out) {
    std::ifstream in(path, std::ios::binary | std::ios::ate);
    if (!in) {
        return false;
    }
    const std::streamsize size = in.tellg();
    if (size <= 0) {
        return false;
    }
    out.resize(static_cast<size_t>(size));
    in.seekg(0, std::ios::beg);
    return static_cast<bool>(in.read(reinterpret_cast<char*>(out.data()), size));
}

}  // namespace

bool readFp16EmbeddingFile(const std::string& path, std::vector<float>& out, std::string& error) {
    std::vector<uint8_t> bytes;
    if (!readBinary(path, bytes) || bytes.size() % sizeof(uint16_t) != 0) {
        error = "Failed to read fp16 embedding file: " + path;
        return false;
    }
    out.resize(bytes.size() / sizeof(uint16_t));
    const auto* values = reinterpret_cast<const uint16_t*>(bytes.data());
    for (size_t i = 0; i < out.size(); ++i) {
        out[i] = fp16_to_fp32(values[i]);
    }
    return true;
}

bool readFp32EmbeddingFile(const std::string& path, std::vector<float>& out, std::string& error) {
    std::vector<uint8_t> bytes;
    if (!readBinary(path, bytes) || bytes.size() % sizeof(float) != 0) {
        error = "Failed to read fp32 embedding file: " + path;
        return false;
    }
    out.resize(bytes.size() / sizeof(float));
    std::copy(bytes.data(), bytes.data() + bytes.size(), reinterpret_cast<uint8_t*>(out.data()));
    return true;
}

bool buildClipInputEmbedding(
    const std::vector<int32_t>& tokens,
    const std::vector<float>& token_embedding,
    const std::vector<float>& position_embedding,
    int hidden_size,
    std::vector<float>& input_embedding,
    std::string& error) {
    if (hidden_size <= 0) {
        error = "Invalid CLIP hidden size.";
        return false;
    }
    const size_t hidden = static_cast<size_t>(hidden_size);
    if (position_embedding.size() < static_cast<size_t>(kClipTokenCount) * hidden) {
        error = "Position embedding file is smaller than 77 tokens.";
        return false;
    }
    if (token_embedding.size() < hidden) {
        error = "Token embedding file is empty.";
        return false;
    }

    const size_t vocab_size = token_embedding.size() / hidden;
    input_embedding.assign(static_cast<size_t>(kClipTokenCount) * hidden, 0.0f);
    for (int pos = 0; pos < kClipTokenCount; ++pos) {
        int32_t token = pos < static_cast<int>(tokens.size()) ? tokens[pos] : kClipEosToken;
        if (token < 0 || static_cast<size_t>(token) >= vocab_size) {
            token = kClipEosToken;
        }
        const size_t token_base = static_cast<size_t>(token) * hidden;
        const size_t pos_base = static_cast<size_t>(pos) * hidden;
        for (size_t dim = 0; dim < hidden; ++dim) {
            input_embedding[pos_base + dim] =
                token_embedding[token_base + dim] + position_embedding[pos_base + dim];
        }
    }
    return true;
}

}  // namespace enclave::diffusion
