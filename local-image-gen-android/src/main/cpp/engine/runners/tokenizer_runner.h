#pragma once

#include <string>
#include <vector>

#ifdef ENCLAVE_DIFFUSION_HAS_TOKENIZERS_CPP
#include <memory>
#include <tokenizers_cpp.h>
#endif

namespace enclave::diffusion {

class TokenizerRunner {
 public:
    bool load(const std::string& tokenizer_json, std::string& error);
    void unload();
    bool isLoaded() const;

    std::vector<int32_t> encode(const std::string& prompt, int max_tokens) const;

 private:
    std::string tokenizer_path_;
#ifdef ENCLAVE_DIFFUSION_HAS_TOKENIZERS_CPP
    std::unique_ptr<tokenizers::Tokenizer> tokenizer_;
#endif
};

}  // namespace enclave::diffusion
