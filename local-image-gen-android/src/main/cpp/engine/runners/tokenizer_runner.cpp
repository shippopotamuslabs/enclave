#include "tokenizer_runner.h"

#include "clip_prompt_utils.h"

#include <algorithm>
#include <cctype>
#include <fstream>
#include <functional>
#include <sstream>

#ifdef ENCLAVE_DIFFUSION_HAS_TOKENIZERS_CPP
#include <tokenizers_cpp.h>
#endif

namespace enclave::diffusion {
namespace {

std::vector<int32_t> normalizeClipTokens(const std::vector<int32_t>& encoded, int max_tokens) {
    const int count = std::max(max_tokens, 0);
    std::vector<int32_t> tokens;
    tokens.reserve(static_cast<size_t>(count));
    if (count == 0) {
        return tokens;
    }
    tokens.push_back(kClipBosToken);
    for (int32_t token : encoded) {
        if (static_cast<int>(tokens.size()) >= count - 1) {
            break;
        }
        if (token != kClipBosToken && token != kClipEosToken) {
            tokens.push_back(token);
        }
    }
    tokens.push_back(kClipEosToken);
    while (static_cast<int>(tokens.size()) < count) {
        tokens.push_back(kClipEosToken);
    }
    tokens.resize(static_cast<size_t>(count));
    tokens.back() = kClipEosToken;
    return tokens;
}

std::vector<int32_t> fallbackEncode(const std::string& prompt, int max_tokens) {
    std::vector<int32_t> ids;
    ids.reserve(static_cast<size_t>(std::max(max_tokens - 2, 0)));
    std::istringstream stream(prompt);
    std::string word;
    std::hash<std::string> hasher;
    while (stream >> word && static_cast<int>(ids.size()) < max_tokens - 2) {
        std::transform(word.begin(), word.end(), word.begin(), [](unsigned char c) {
            return static_cast<char>(std::tolower(c));
        });
        ids.push_back(1 + static_cast<int32_t>(hasher(word) % 49400));
    }
    return normalizeClipTokens(ids, max_tokens);
}

}  // namespace

bool TokenizerRunner::load(const std::string& tokenizer_json, std::string& error) {
    std::ifstream in(tokenizer_json, std::ios::binary);
    if (!in) {
        error = "Missing tokenizer.json.";
        return false;
    }
    tokenizer_path_ = tokenizer_json;
#ifdef ENCLAVE_DIFFUSION_HAS_TOKENIZERS_CPP
    std::string blob((std::istreambuf_iterator<char>(in)), std::istreambuf_iterator<char>());
    tokenizer_ = tokenizers::Tokenizer::FromBlobJSON(blob);
    if (!tokenizer_) {
        error = "Failed to create tokenizer from tokenizer.json.";
        tokenizer_path_.clear();
        return false;
    }
#endif
    return true;
}

void TokenizerRunner::unload() {
#ifdef ENCLAVE_DIFFUSION_HAS_TOKENIZERS_CPP
    tokenizer_.reset();
#endif
    tokenizer_path_.clear();
}

bool TokenizerRunner::isLoaded() const { return !tokenizer_path_.empty(); }

std::vector<int32_t> TokenizerRunner::encode(const std::string& prompt, int max_tokens) const {
#ifdef ENCLAVE_DIFFUSION_HAS_TOKENIZERS_CPP
    if (tokenizer_) {
        return normalizeClipTokens(tokenizer_->Encode(prompt), max_tokens);
    }
#endif
    return fallbackEncode(prompt, max_tokens);
}

}  // namespace enclave::diffusion
