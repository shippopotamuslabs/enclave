// JNI bridge for llama.cpp Android integration
// Provides thin JNI layer to expose llama.cpp functionality to Kotlin

#include <jni.h>
#include <string>
#include <memory>
#include <atomic>
#include <mutex>
#include <optional>
#include <vector>
#include <algorithm>
#include <cstdlib>
#include <chrono>
#include <android/log.h>

#include "llama.h"

// Forward declarations of common helper functions (from common.h)
static inline void llama_batch_clear(struct llama_batch & batch) {
    batch.n_tokens = 0;
}

static inline void llama_batch_add(
        struct llama_batch & batch,
        llama_token id,
        llama_pos pos,
        const std::vector<llama_seq_id> & seq_ids,
        bool logits) {
    batch.token   [batch.n_tokens] = id;
    batch.pos     [batch.n_tokens] = pos;
    batch.n_seq_id[batch.n_tokens] = seq_ids.size();
    for (size_t i = 0; i < seq_ids.size(); ++i) {
        batch.seq_id[batch.n_tokens][i] = seq_ids[i];
    }
    batch.logits  [batch.n_tokens] = logits;
    batch.n_tokens++;
}

// Global state for the llama context - using raw pointers since llama_model/llama_context are opaque
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static llama_sampler* g_sampler = nullptr;
static llama_batch* g_batch = nullptr;  // Reusable batch like official example
static std::atomic<bool> g_generating(false);
static std::mutex g_mutex;

// Helper to get vocab from model (must be after g_model declaration)
static inline const llama_vocab* get_model_vocab() {
    return llama_model_get_vocab(g_model);
}

// Callback interface
static jobject g_callback = nullptr;
static JNIEnv* g_callbackEnv = nullptr;

extern "C" {

// Initialize llama backend once
static bool g_initialized = false;

// Helper to convert JNI string to std::string
static std::string jstringToString(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string str(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return str;
}

// Helper to get callback method
static jmethodID getCallbackMethod(JNIEnv* env, jobject callback, const char* name, const char* sig) {
    jclass cls = env->GetObjectClass(callback);
    return env->GetMethodID(cls, name, sig);
}

JNIEXPORT jboolean JNICALL
Java_com_browntowndev_pocketcrew_inference_llama_JniLlamaEngine_nativeLoadModel(
    JNIEnv* env,
    jobject /* this */,
    jstring modelPath,
    jint contextSize,
    jint threads,
    jint batchSize,
    jint gpuLayers
) {
    // Initialize backend if not already done
    if (!g_initialized) {
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== INITIALIZING LLAMA BACKEND ===");

        llama_backend_init();
        g_initialized = true;

        // Check GPU offload support
        bool supports_gpu = llama_supports_gpu_offload();

        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== AVAILABLE BACKENDS ===");
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "GPU offload support: %s", supports_gpu ? "YES" : "NO");
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "===========================");

        // Note: SVE and other CPU feature detection has been moved to ggml.h
        // For CPU-only mode, we rely on llama's internal detection
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Running in CPU-only mode (GPU disabled)");
    }

    std::lock_guard<std::mutex> lock(g_mutex);

    // Unload existing model if any
    if (g_ctx) {
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Unloading existing context");
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Unloading existing model");
        llama_model_free(g_model);
        g_model = nullptr;
    }

    std::string path = jstringToString(env, modelPath);

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== LOADING MODEL ===");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Model path: %s", path.c_str());
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Requested contextSize: %d", contextSize);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Requested threads: %d", threads);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Requested batchSize: %d", batchSize);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Requested GPU layers: %d", gpuLayers);

    // Model params
    llama_model_params mparams = llama_model_default_params();

    // Use GPU if GPU layers are requested
    // Note: llama.cpp will automatically detect and use available GPU backends
    int actualGpuLayers = gpuLayers;

    mparams.n_gpu_layers = actualGpuLayers;
    mparams.use_mmap = false;  // Disable mmap for mobile

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== GPU CONFIGURATION ===");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "GPU layers requested: %d", gpuLayers);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "GPU layers ACTUALLY USED: %d", actualGpuLayers);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Execution mode: %s", actualGpuLayers > 0 ? "GPU ACCELERATED" : "CPU ONLY");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "==========================");

    auto load_start = std::chrono::high_resolution_clock::now();

    // Load model
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Calling llama_model_load_from_file...");
    g_model = llama_model_load_from_file(path.c_str(), mparams);
    if (!g_model) {
        __android_log_print(ANDROID_LOG_ERROR, "llama-jni", "FAILED to load model from: %s", path.c_str());
        return JNI_FALSE;
    }

    auto load_end = std::chrono::high_resolution_clock::now();
    double load_ms = std::chrono::duration<double, std::milli>(load_end - load_start).count();

    // Log successful load
    const int32_t n_vocab = llama_vocab_n_tokens(get_model_vocab());

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== MODEL LOADED SUCCESSFULLY ===");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Load time: %.2f ms", load_ms);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Vocabulary size: %d", n_vocab);

    // Log GPU info if available
    if (actualGpuLayers > 0) {
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "*** GPU ACCELERATION ACTIVE ***");
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Offloaded %d layers to GPU", actualGpuLayers);
    } else {
        __android_log_print(ANDROID_LOG_WARN, "llama-jni", "*** RUNNING IN CPU-ONLY MODE ***");
    }
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "==========================");

    // Context params
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== CREATING CONTEXT ===");
    llama_context_params cparams = llama_context_default_params();

    // Log default values before modification
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEFAULT params: n_ctx=%d, n_batch=%d, n_ubatch=%d, n_threads=%d, n_threads_batch=%d",
        cparams.n_ctx, cparams.n_batch, cparams.n_ubatch, cparams.n_threads, cparams.n_threads_batch);

    // Cap context size at 2048 for stability (matching official llama.android example)
    // Large contexts can cause memory issues on mobile devices
    int cappedContextSize = contextSize > 2048 ? 2048 : contextSize;
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Context size: requested=%d, using=%d", contextSize, cappedContextSize);

    cparams.n_ctx = cappedContextSize;

    // Use at least 4 threads for reasonable CPU performance
    // Single thread is extremely slow on mobile
    int actualThreads = (threads > 0) ? threads : 4;
    cparams.n_threads = actualThreads;
    cparams.n_threads_batch = actualThreads;

    // Set n_batch and n_ubatch to match for stability
    // Having n_batch > n_ubatch can cause issues
    cparams.n_batch = 512;   // Match default n_ubatch
    cparams.n_ubatch = 512;

    cparams.offload_kqv = (actualGpuLayers > 0);  // Use GPU only if GPU layers configured
    cparams.flash_attn_type = LLAMA_FLASH_ATTN_TYPE_DISABLED;  // Disable for CPU mode stability

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "FINAL params: n_ctx=%d, n_batch=%d, n_ubatch=%d, n_threads=%d, n_threads_batch=%d, offload_kqv=%d",
        cparams.n_ctx, cparams.n_batch, cparams.n_ubatch, cparams.n_threads, cparams.n_threads_batch, cparams.offload_kqv);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Context n_ctx: %d", cparams.n_ctx);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Context n_threads: %d", cparams.n_threads);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Context n_threads_batch: %d", cparams.n_threads_batch);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Context offload_kqv: %s", cparams.offload_kqv ? "true" : "false");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Context flash_attn_type: %d", (int)cparams.flash_attn_type);

    // Create context
    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        __android_log_print(ANDROID_LOG_ERROR, "llama-jni", "FAILED to create context");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    // Create reusable batch (like official example)
    // Note: llama_batch_init returns llama_batch, not pointer
    static llama_batch s_batch;  // Static to persist
    s_batch = llama_batch_init(512, 0, 1);
    g_batch = &s_batch;
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Created reusable batch");

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== CONTEXT CREATED SUCCESSFULLY ===");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "==========================");

    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_browntowndev_pocketcrew_inference_llama_JniLlamaEngine_nativeUnloadModel(
    JNIEnv* env,
    jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);

    // Stop any ongoing generation
    if (g_generating.exchange(false)) {
        // Generation will be stopped by llama_decode returning
    }

    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }
    if (g_batch) {
        llama_batch_free(*g_batch);
        delete g_batch;
        g_batch = nullptr;
    }
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Model and context unloaded");
}

JNIEXPORT void JNICALL
Java_com_browntowndev_pocketcrew_inference_llama_JniLlamaEngine_nativeClearKvCache(
    JNIEnv* env,
    jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    // Note: llama_kv_cache_clear has been removed in the new API.
    // The KV cache is automatically managed. If you need to clear it,
    // you would need to unload and reload the model or use context reset.
    // For now, this is a no-op.
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "KV cache clear - no-op (API removed)");
}

JNIEXPORT void JNICALL
Java_com_browntowndev_pocketcrew_inference_llama_JniLlamaEngine_nativeStartCompletion(
    JNIEnv* env,
    jobject /* this */,
    jstring prompt,
    jfloat temperature,
    jint topK,
    jfloat topP,
    jint maxTokens,
    jfloat repeatPenalty,
    jobject callback
) {
    // Store callback globally
    g_callback = env->NewGlobalRef(callback);
    g_callbackEnv = env;

    // Check if model is loaded
    if (!g_model) {
        g_generating = false;
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Model not loaded");
        return;
    }

    // Check if model has a vocabulary (required for tokenization)
    const int32_t n_vocab = llama_vocab_n_tokens(get_model_vocab());
    if (n_vocab <= 0) {
        g_generating = false;
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),
            "Model does not have a vocabulary. This GGUF file may be quantized without a tokenizer. "
            "Please use a model file that includes the tokenizer or a different model format.");
        return;
    }

    std::string promptStr = jstringToString(env, prompt);

    // Initialize sampler chain with config values (official llama.cpp sampler)
    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());

    // Add temperature (default 0.7 if not set)
    float temp = temperature > 0 ? temperature : 0.7f;
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(temp));

    // Add top-k filtering (default 40 if not set)
    int top_k = topK > 0 ? topK : 40;
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(top_k));

    // Add top-p filtering (default 0.9 if not set)
    float top_p_val = topP > 0 ? topP : 0.9f;
    llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(top_p_val, 1));

    // Add mirostat v2 for thinking models (tau=5.0, eta=0.1 recommended)
    uint32_t seed = static_cast<uint32_t>(time(nullptr));
    llama_sampler_chain_add(g_sampler, llama_sampler_init_mirostat_v2(seed, 5.0f, 0.1f));

    // Add repetition penalties (last_n=512, repeat from config or default 1.28)
    int repeat_last_n = 512;
    float repeat_pen = repeatPenalty > 1.0f ? repeatPenalty : 1.28f;
    llama_sampler_chain_add(g_sampler, llama_sampler_init_penalties(repeat_last_n, repeat_pen, 0.0f, 0.0f));

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== SAMPLER CHAIN CONFIGURED ===");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Temperature: %.2f", temp);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Top-K: %d", top_k);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Top-P: %.2f", top_p_val);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Mirostat: v2 (tau=5.0, eta=0.1)");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Repeat penalty: %.2f (last_n=%d)", repeat_pen, repeat_last_n);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "===========================");

    g_generating = true;

    // Get context info
    const int32_t n_ctx = llama_n_ctx(g_ctx);

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== STARTING COMPLETION ===");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Prompt length: %d chars", (int)promptStr.size());
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Context size: %d", n_ctx);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Temperature: %.2f", temperature);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Top-K: %d", topK);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Top-P: %.2f", topP);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Max tokens: %d", maxTokens);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Repeat penalty: %.2f", repeatPenalty);

    // Check if context is valid
    if (!g_ctx || n_ctx == 0) {
        g_generating = false;
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),
            "Invalid context: ctx is null or n_ctx=0");
        return;
    }

    // Get BOS token
    llama_token bos_token = llama_vocab_bos(get_model_vocab());
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "BOS token: %d", bos_token);

    // Tokenize prompt - first call to get required buffer size
    // Note: using add_bos=true to add beginning-of-sequence token
    std::vector<llama_token> tokens(4096);
    const int required_tokens = llama_tokenize(get_model_vocab(), promptStr.c_str(), promptStr.size(), tokens.data(), 4096, true, true);
    if (required_tokens <= 0) {
        g_generating = false;
        std::string errorMsg = "Failed to tokenize prompt: required_tokens=" + std::to_string(required_tokens);
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), errorMsg.c_str());
        return;
    }

    const int n_prompt_tokens = required_tokens;
    int n_generated_tokens = 0;
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Prompt tokens: %d", n_prompt_tokens);
    // Debug: print first few tokens
    for (int i = 0; i < std::min(5, n_prompt_tokens); i++) {
        const char* tokentext = llama_vocab_get_text(get_model_vocab(), tokens[i]);
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "  token[%d] = %d: '%s'", i, tokens[i], tokentext);
    }

    // Callback methods
    jmethodID onTokenMethod = getCallbackMethod(env, g_callback, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = getCallbackMethod(env, g_callback, "onComplete", "(II)V");
    jmethodID onErrorMethod = getCallbackMethod(env, g_callback, "onError", "(Ljava/lang/String;)V");

    // Check if model context can hold prompt
    if (n_prompt_tokens > n_ctx) {
        g_generating = false;
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Prompt too long for context");
        return;
    }

    // Log timing info
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Evaluating prompt: %d tokens", n_prompt_tokens);

    // Debug: verify tokens vector
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: tokens size=%zu", tokens.size());

    // Evaluate prompt using llama_batch_get_one (API available in this version)
    auto prompt_start = std::chrono::high_resolution_clock::now();

    // Pre-decode validation - check model and context state
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: Pre-decode validation");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: g_model=%p, g_ctx=%p", (void*)g_model, (void*)g_ctx);

    if (g_model) {
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: model n_vocab=%d, n_ctx_train=%d",
            llama_vocab_n_tokens(get_model_vocab()), llama_model_n_ctx_train(g_model));
    }

    // Check context state size (this validates the context is properly initialized)
    size_t ctx_state_size = llama_state_get_size(g_ctx);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: context state size=%zu", ctx_state_size);

    // Use the reusable batch - clear it first (like official example)
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: Using reusable batch for prompt eval");
    llama_batch_clear(*g_batch);

    // Add all prompt tokens using llama_batch_add (like official example)
    for (int i = 0; i < n_prompt_tokens; i++) {
        llama_batch_add(*g_batch, tokens[i], i, { 0 }, false);
    }
    // Only the last token gets logits
    g_batch->logits[g_batch->n_tokens - 1] = true;

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: Added %d tokens to batch", g_batch->n_tokens);

    // Debug context info
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: ctx=%p, n_ctx=%d", (void*)g_ctx, llama_n_ctx(g_ctx));

    // Call llama_decode with the batch
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: About to call llama_decode...");
    int decode_result = llama_decode(g_ctx, *g_batch);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "llama_decode returned %d", decode_result);

    // Debug: check the logits after prompt eval
    float* logits = llama_get_logits(g_ctx);
    if (logits) {
        // Find the top token
        int top_token = 0;
        float top_score = logits[0];
        for (int i = 1; i < n_vocab; i++) {
            if (logits[i] > top_score) {
                top_score = logits[i];
                top_token = i;
            }
        }
        const char* top_token_text = llama_vocab_get_text(get_model_vocab(), top_token);
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: Top token after prompt: id=%d, score=%.2f, text='%s'",
            top_token, top_score, top_token_text);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, "llama-jni", "DEBUG: logits is NULL!");
    }

    auto prompt_end = std::chrono::high_resolution_clock::now();
    double prompt_ms = std::chrono::duration<double, std::milli>(prompt_end - prompt_start).count();
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Prompt evaluation took %.2f ms (%.2f ms/token)",
        prompt_ms, prompt_ms / n_prompt_tokens);

    if (decode_result != 0) {
        g_generating = false;
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Failed to decode prompt");
        return;
    }

    // Keep the batch for reuse in generation loop

    // Sample and generate tokens
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== GENERATING TOKENS ===");

    // Verify sampler is valid
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: sampler=%p", (void*)g_sampler);
    if (!g_sampler) {
        __android_log_print(ANDROID_LOG_ERROR, "llama-jni", "ERROR: sampler is NULL!");
        g_generating = false;
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Sampler is NULL");
        return;
    }
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: Sampler is valid");

    auto gen_start = std::chrono::high_resolution_clock::now();

    // Get special tokens and vocab
    llama_token eos_token = llama_vocab_eos(get_model_vocab());
    const int vocab_size = llama_vocab_n_tokens(get_model_vocab());
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: EOS=%d, vocab_size=%d, n_prompt_tokens=%d", eos_token, vocab_size, n_prompt_tokens);

    // Generation loop using official llama.cpp sampler
    while (g_generating && n_generated_tokens < maxTokens) {
        // Sample using official llama.cpp sampler chain
        // idx=-1 means sample from the last token's logits
        llama_token token = llama_sampler_sample(g_sampler, g_ctx, -1);

        // Check for EOS
        if (token == eos_token || token == llama_vocab_eot(get_model_vocab())) {
            __android_log_print(ANDROID_LOG_DEBUG, "llama-jni", "End of token (EOS/EOT) reached at token %d", n_generated_tokens);
            break;
        }

        // Accept the token (updates sampler state for next iteration)
        llama_sampler_accept(g_sampler, token);

        // Get token text - use llama_token_to_piece to convert token to string
        char tokenBuffer[64];
        int tokenLen = llama_token_to_piece(get_model_vocab(), token, tokenBuffer, sizeof(tokenBuffer), 0, false);
        if (tokenLen > 0) {
            tokenBuffer[tokenLen] = '\0';
            // Replace BPE word-start marker (U+2581 = ▁) with space
            // This character appears at the start of tokens that follow a space
            for (int i = 0; i < tokenLen; i++) {
                if ((unsigned char)tokenBuffer[i] == 0xE2 &&
                    i + 2 < tokenLen &&
                    (unsigned char)tokenBuffer[i+1] == 0x96 &&
                    (unsigned char)tokenBuffer[i+2] == 0x81) {
                    tokenBuffer[i] = ' ';
                    // Shift remaining bytes left by 2
                    for (int j = i + 1; j < tokenLen - 2; j++) {
                        tokenBuffer[j] = tokenBuffer[j + 2];
                    }
                    tokenLen -= 2;
                    tokenBuffer[tokenLen] = '\0';
                    break;
                }
            }
        }

        jstring tokenStr = env->NewStringUTF(tokenBuffer);
        env->CallVoidMethod(g_callback, onTokenMethod, tokenStr);
        env->DeleteLocalRef(tokenStr);

        // Accept the token (updates sampler state for next iteration)
        llama_sampler_accept(g_sampler, token);

        // Evaluate token using reusable batch (like official example)
        auto tok_start = std::chrono::high_resolution_clock::now();
        llama_batch_clear(*g_batch);
        // Position is sequential: prompt tokens are at 0..n_prompt_tokens-1
        // First generated token is at position n_prompt_tokens
        llama_batch_add(*g_batch, token, n_prompt_tokens + n_generated_tokens, { 0 }, true);

        if (llama_decode(g_ctx, *g_batch) != 0) {
            break;
        }
        auto tok_end = std::chrono::high_resolution_clock::now();
        double tok_ms = std::chrono::duration<double, std::milli>(tok_end - tok_start).count();

        n_generated_tokens++;
    }
    auto gen_end = std::chrono::high_resolution_clock::now();
    double gen_ms = std::chrono::duration<double, std::milli>(gen_end - gen_start).count();
    double ms_per_token = n_generated_tokens > 0 ? gen_ms / n_generated_tokens : 0;

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== GENERATION COMPLETE ===");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Generated %d tokens in %.2f ms", n_generated_tokens, gen_ms);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Average: %.2f ms/token", ms_per_token);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Estimated speed: %.1f tokens/second", 1000.0 / ms_per_token);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "==========================");

    // Signal completion
    env->CallVoidMethod(g_callback, onCompleteMethod, n_prompt_tokens, n_generated_tokens);

    // Cleanup callback
    if (g_callback) {
        env->DeleteGlobalRef(g_callback);
        g_callback = nullptr;
        g_callbackEnv = nullptr;
    }

    g_generating = false;
}

JNIEXPORT void JNICALL
Java_com_browntowndev_pocketcrew_inference_llama_JniLlamaEngine_nativeStopCompletion(
    JNIEnv* env,
    jobject /* this */
) {
    g_generating = false;
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Generation stopped by user");
}

} // extern "C"
