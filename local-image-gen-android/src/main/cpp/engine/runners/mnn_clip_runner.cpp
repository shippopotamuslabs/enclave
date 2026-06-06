#include "mnn_clip_runner.h"

#include "clip_prompt_utils.h"

#include <algorithm>
#include <fstream>

#ifdef ENCLAVE_DIFFUSION_HAS_MNN
#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>
#endif

namespace enclave::diffusion {
namespace {

bool exists(const std::string& path) {
    std::ifstream in(path, std::ios::binary);
    return static_cast<bool>(in);
}

#ifdef ENCLAVE_DIFFUSION_HAS_MNN
struct MnnSession {
    std::shared_ptr<MNN::Interpreter> interpreter;
    MNN::Session* session = nullptr;
    int hidden = 0;
};

bool sessionReady(const MnnSession& session) {
    return session.interpreter != nullptr && session.session != nullptr && session.hidden > 0;
}

bool createSession(const std::string& path, int hidden, MnnSession& out, std::string& error) {
    out.interpreter.reset(MNN::Interpreter::createFromFile(path.c_str()));
    if (!out.interpreter) {
        error = "Failed to create MNN interpreter for " + path;
        return false;
    }
    MNN::ScheduleConfig config;
    config.type = MNN_FORWARD_CPU;
    config.numThread = 2;
    MNN::BackendConfig backend_config;
    backend_config.precision = MNN::BackendConfig::Precision_Low;
    config.backendConfig = &backend_config;
    out.session = out.interpreter->createSession(config);
    if (out.session == nullptr) {
        error = "Failed to create MNN CLIP session.";
        out.interpreter.reset();
        return false;
    }
    out.hidden = hidden;
    return true;
}

bool runClipSession(
    MnnSession& clip,
    const std::vector<float>& input_embedding,
    std::vector<float>& hidden_states,
    std::vector<float>* pooled,
    std::string& error) {
    MNN::Tensor* input = clip.interpreter->getSessionInput(clip.session, "input_embedding");
    if (input == nullptr) {
        error = "MNN CLIP input tensor input_embedding not found.";
        return false;
    }
    std::vector<int> dims {1, kClipTokenCount, clip.hidden};
    clip.interpreter->resizeTensor(input, dims);
    clip.interpreter->resizeSession(clip.session);

    MNN::Tensor input_host(input, MNN::Tensor::CAFFE);
    std::copy(input_embedding.begin(), input_embedding.end(), input_host.host<float>());
    input->copyFromHostTensor(&input_host);
    if (clip.interpreter->runSession(clip.session) != MNN::NO_ERROR) {
        error = "MNN CLIP session execution failed.";
        return false;
    }

    MNN::Tensor* output = clip.interpreter->getSessionOutput(clip.session, "last_hidden_state");
    if (output == nullptr) {
        error = "MNN CLIP output tensor last_hidden_state not found.";
        return false;
    }
    MNN::Tensor output_host(output, MNN::Tensor::CAFFE);
    output->copyToHostTensor(&output_host);
    hidden_states.assign(
        output_host.host<float>(),
        output_host.host<float>() + static_cast<size_t>(kClipTokenCount) * static_cast<size_t>(clip.hidden));

    if (pooled != nullptr) {
        MNN::Tensor* pooled_output = clip.interpreter->getSessionOutput(clip.session, "pooled_output");
        if (pooled_output != nullptr) {
            MNN::Tensor pooled_host(pooled_output, MNN::Tensor::CAFFE);
            pooled_output->copyToHostTensor(&pooled_host);
            const size_t pooled_count = static_cast<size_t>(pooled_host.elementSize());
            pooled->assign(pooled_host.host<float>(), pooled_host.host<float>() + pooled_count);
        }
    }
    return true;
}
#endif

}  // namespace

struct MnnClipRunner::Impl {
    PackManifest manifest;
    bool loaded = false;
    std::vector<float> token_emb;
    std::vector<float> token_emb_2;
    std::vector<float> pos_emb;
    std::vector<float> pos_emb_2;
#ifdef ENCLAVE_DIFFUSION_HAS_MNN
    MnnSession clip;
    MnnSession clip_2;
#endif
};

MnnClipRunner::MnnClipRunner() : impl_(std::make_unique<Impl>()) {}

MnnClipRunner::~MnnClipRunner() = default;

bool MnnClipRunner::load(const PackManifest& manifest, std::string& error) {
#ifndef ENCLAVE_DIFFUSION_HAS_MNN
    (void)manifest;
    error = "MNN CLIP support is not linked into this build.";
    return false;
#else
    unload();
    if (manifest.sdxl) {
        if (!exists(manifest.clip_mnn) || !exists(manifest.clip_2_mnn)) {
            error = "SDXL requires clip.mnn and clip_2.mnn.";
            return false;
        }
    } else if (!exists(manifest.clip_v2_mnn)) {
        error = "SD1.5 requires clip_v2.mnn.";
        return false;
    }
    if (!readFp16EmbeddingFile(manifest.token_emb_bin, impl_->token_emb, error) ||
        !readFp32EmbeddingFile(manifest.pos_emb_bin, impl_->pos_emb, error)) {
        return false;
    }
    if (manifest.sdxl &&
        (!readFp16EmbeddingFile(manifest.token_emb_2_bin, impl_->token_emb_2, error) ||
         !readFp32EmbeddingFile(manifest.pos_emb_2_bin, impl_->pos_emb_2, error))) {
        return false;
    }

    if (manifest.sdxl) {
        if (!createSession(manifest.clip_mnn, 768, impl_->clip, error) ||
            !createSession(manifest.clip_2_mnn, 1280, impl_->clip_2, error)) {
            unload();
            return false;
        }
    } else if (!createSession(manifest.clip_v2_mnn, 768, impl_->clip, error)) {
        unload();
        return false;
    }
    impl_->manifest = manifest;
    impl_->loaded = true;
    return true;
#endif
}

void MnnClipRunner::unload() {
    impl_->loaded = false;
    impl_->manifest = PackManifest{};
    impl_->token_emb.clear();
    impl_->token_emb_2.clear();
    impl_->pos_emb.clear();
    impl_->pos_emb_2.clear();
#ifdef ENCLAVE_DIFFUSION_HAS_MNN
    impl_->clip = MnnSession{};
    impl_->clip_2 = MnnSession{};
#endif
}

bool MnnClipRunner::isLoaded() const { return impl_->loaded; }

bool MnnClipRunner::encode(
    const std::string& prompt,
    const std::string& negative_prompt,
    const TokenizerRunner& tokenizer,
    int output_width,
    int output_height,
    ClipConditioning& conditioning,
    std::string& error) {
#ifndef ENCLAVE_DIFFUSION_HAS_MNN
    (void)prompt;
    (void)negative_prompt;
    (void)tokenizer;
    (void)output_width;
    (void)output_height;
    (void)conditioning;
    error = "MNN CLIP support is not linked into this build.";
    return false;
#else
    if (!impl_->loaded) {
        error = "CLIP runner is not loaded.";
        return false;
    }
    if (!sessionReady(impl_->clip) || (impl_->manifest.sdxl && !sessionReady(impl_->clip_2))) {
        error = "CLIP MNN session was unloaded; retry generation.";
        return false;
    }
    const std::vector<int32_t> positive = tokenizer.encode(prompt, kClipTokenCount);
    const std::vector<int32_t> negative = tokenizer.encode(negative_prompt, kClipTokenCount);

    auto encodeOne = [&](const std::vector<int32_t>& tokens,
                         MnnSession& session,
                         const std::vector<float>& token_emb,
                         const std::vector<float>& pos_emb,
                         std::vector<float>& hidden,
                         std::vector<float>* pooled) -> bool {
        std::vector<float> input;
        return buildClipInputEmbedding(tokens, token_emb, pos_emb, session.hidden, input, error) &&
               runClipSession(session, input, hidden, pooled, error);
    };

    conditioning = ClipConditioning{};
    conditioning.sdxl = impl_->manifest.sdxl;
    if (!impl_->manifest.sdxl) {
        std::vector<float> neg_hidden;
        std::vector<float> pos_hidden;
        if (!encodeOne(negative, impl_->clip, impl_->token_emb, impl_->pos_emb, neg_hidden, nullptr) ||
            !encodeOne(positive, impl_->clip, impl_->token_emb, impl_->pos_emb, pos_hidden, nullptr)) {
            return false;
        }
        conditioning.hidden_size = 768;
        conditioning.hidden_states.reserve(neg_hidden.size() + pos_hidden.size());
        conditioning.hidden_states.insert(conditioning.hidden_states.end(), neg_hidden.begin(), neg_hidden.end());
        conditioning.hidden_states.insert(conditioning.hidden_states.end(), pos_hidden.begin(), pos_hidden.end());
        return true;
    }

    std::vector<float> neg_1;
    std::vector<float> pos_1;
    std::vector<float> neg_2;
    std::vector<float> pos_2;
    std::vector<float> neg_pool;
    std::vector<float> pos_pool;
    if (!encodeOne(negative, impl_->clip, impl_->token_emb, impl_->pos_emb, neg_1, nullptr) ||
        !encodeOne(positive, impl_->clip, impl_->token_emb, impl_->pos_emb, pos_1, nullptr) ||
        !encodeOne(negative, impl_->clip_2, impl_->token_emb_2, impl_->pos_emb_2, neg_2, &neg_pool) ||
        !encodeOne(positive, impl_->clip_2, impl_->token_emb_2, impl_->pos_emb_2, pos_2, &pos_pool)) {
        return false;
    }

    conditioning.hidden_size = 2048;
    conditioning.hidden_states.resize(static_cast<size_t>(2 * kClipTokenCount * 2048));
    for (int batch = 0; batch < 2; ++batch) {
        const std::vector<float>& h1 = batch == 0 ? neg_1 : pos_1;
        const std::vector<float>& h2 = batch == 0 ? neg_2 : pos_2;
        const size_t batch_base = static_cast<size_t>(batch * kClipTokenCount * 2048);
        for (int token = 0; token < kClipTokenCount; ++token) {
            const size_t dst = batch_base + static_cast<size_t>(token * 2048);
            const size_t src1 = static_cast<size_t>(token * 768);
            const size_t src2 = static_cast<size_t>(token * 1280);
            std::copy(h1.begin() + src1, h1.begin() + src1 + 768, conditioning.hidden_states.begin() + dst);
            std::copy(h2.begin() + src2, h2.begin() + src2 + 1280, conditioning.hidden_states.begin() + dst + 768);
        }
    }
    conditioning.pooled.reserve(2560);
    conditioning.pooled.insert(conditioning.pooled.end(), neg_pool.begin(), neg_pool.end());
    conditioning.pooled.insert(conditioning.pooled.end(), pos_pool.begin(), pos_pool.end());
    conditioning.time_ids = {
        static_cast<float>(output_height),
        static_cast<float>(output_width),
        0.0f,
        0.0f,
        static_cast<float>(output_height),
        static_cast<float>(output_width),
    };
    return true;
#endif
}

}  // namespace enclave::diffusion
