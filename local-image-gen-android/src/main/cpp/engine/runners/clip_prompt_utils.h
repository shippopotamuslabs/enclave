#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace enclave::diffusion {

constexpr int kClipTokenCount = 77;
constexpr int32_t kClipBosToken = 49406;
constexpr int32_t kClipEosToken = 49407;

bool readFp16EmbeddingFile(const std::string& path, std::vector<float>& out, std::string& error);

bool readFp32EmbeddingFile(const std::string& path, std::vector<float>& out, std::string& error);

bool buildClipInputEmbedding(
    const std::vector<int32_t>& tokens,
    const std::vector<float>& token_embedding,
    const std::vector<float>& position_embedding,
    int hidden_size,
    std::vector<float>& input_embedding,
    std::string& error);

}  // namespace enclave::diffusion
