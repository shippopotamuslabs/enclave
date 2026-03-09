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
#include "ggml.h"

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

        // Check all available backends
        int has_vulkan = ggml_cpu_has_vulkan();
        int has_cuda = ggml_cpu_has_cuda();
        int has_metal = ggml_cpu_has_metal();
        int has_blas = ggml_cpu_has_blas();

        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== AVAILABLE BACKENDS ===");
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Vulkan: %s", has_vulkan ? "YES" : "NO");
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "CUDA: %s", has_cuda ? "YES" : "NO");
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Metal: %s", has_metal ? "YES" : "NO");
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "BLAS: %s", has_blas ? "YES" : "NO");
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Note: OpenCL is compiled in but has no detection function");
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "===========================");

        // SVE detection for debugging Pixel 8 Pro crashes
        int has_sve = ggml_cpu_has_sve();
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "SVE support detected: %s (0=good, non-zero=potential crash on Tensor G3)", has_sve ? "YES" : "NO");

        // Also check compile-time SVE flag
        #ifdef GGML_CPU_ARM_ENABLE_SVE
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "COMPILE-TIME SVE: ENABLED (this will crash on Tensor G3!)");
        #else
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "COMPILE-TIME SVE: DISABLED (safe for Tensor G3)");
        #endif
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
        llama_free_model(g_model);
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

    // Check Vulkan availability
    int has_vulkan = ggml_cpu_has_vulkan();

    // Use GPU if either Vulkan is detected OR if GPU layers are requested
    // Note: When built with GGML_OPENCL=ON, OpenCL will be used automatically
    // for GPU layers even without Vulkan detection
    int actualGpuLayers = gpuLayers;

    mparams.n_gpu_layers = actualGpuLayers;
    mparams.use_mmap = false;  // Disable mmap for mobile

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== GPU CONFIGURATION ===");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Vulkan detected: %s", has_vulkan ? "YES" : "NO");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "GPU layers requested: %d", gpuLayers);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "GPU layers ACTUALLY USED: %d", actualGpuLayers);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Execution mode: %s", actualGpuLayers > 0 ? "GPU ACCELERATED (Vulkan)" : "CPU ONLY");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "==========================");

    auto load_start = std::chrono::high_resolution_clock::now();

    // Load model
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Calling llama_load_model_from_file...");
    g_model = llama_load_model_from_file(path.c_str(), mparams);
    if (!g_model) {
        __android_log_print(ANDROID_LOG_ERROR, "llama-jni", "FAILED to load model from: %s", path.c_str());
        return JNI_FALSE;
    }

    auto load_end = std::chrono::high_resolution_clock::now();
    double load_ms = std::chrono::duration<double, std::milli>(load_end - load_start).count();

    // Log successful load
    const int32_t n_vocab = llama_n_vocab(g_model);

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
    cparams.flash_attn = false;  // Disable for CPU mode stability

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "FINAL params: n_ctx=%d, n_batch=%d, n_ubatch=%d, n_threads=%d, n_threads_batch=%d, offload_kqv=%d",
        cparams.n_ctx, cparams.n_batch, cparams.n_ubatch, cparams.n_threads, cparams.n_threads_batch, cparams.offload_kqv);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Context n_ctx: %d", cparams.n_ctx);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Context n_threads: %d", cparams.n_threads);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Context n_threads_batch: %d", cparams.n_threads_batch);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Context offload_kqv: %s", cparams.offload_kqv ? "true" : "false");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Context flash_attn: %s", cparams.flash_attn ? "true" : "false");

    // Create context
    g_ctx = llama_new_context_with_model(g_model, cparams);
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
        llama_free_model(g_model);
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
    if (g_ctx) {
        llama_kv_cache_clear(g_ctx);
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "KV cache cleared");
    }
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
    const int32_t n_vocab = llama_n_vocab(g_model);
    if (n_vocab <= 0) {
        g_generating = false;
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),
            "Model does not have a vocabulary. This GGUF file may be quantized without a tokenizer. "
            "Please use a model file that includes the tokenizer or a different model format.");
        return;
    }

    std::string promptStr = jstringToString(env, prompt);

    // Initialize sampler chain - greedy only (like official example)
    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());

    // Greedy sampler - always picks highest probability token
    llama_sampler_chain_add(g_sampler, llama_sampler_init_greedy());

    // Note: Repeat penalty removed - causing issues with special tokens
    // Will implement repetition filtering in sampling loop instead

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
    llama_token bos_token = llama_token_bos(g_model);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "BOS token: %d", bos_token);

    // Tokenize prompt - first call to get required buffer size
    // Note: using add_bos=true to add beginning-of-sequence token
    std::vector<llama_token> tokens(4096);
    const int required_tokens = llama_tokenize(g_model, promptStr.c_str(), promptStr.size(), tokens.data(), 4096, true, true);
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
        const char* tokentext = llama_token_get_text(g_model, tokens[i]);
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
            llama_n_vocab(g_model), llama_n_ctx_train(g_model));
    }

    // Check context state size (this validates the context is properly initialized)
    size_t ctx_state_size = llama_get_state_size(g_ctx);
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
        const char* top_token_text = llama_token_get_text(g_model, top_token);
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
    llama_token eos_token = llama_token_eos(g_model);
    const int vocab_size = llama_n_vocab(g_model);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: EOS=%d, vocab_size=%d, n_prompt_tokens=%d", eos_token, vocab_size, n_prompt_tokens);

    // Manual top-k sampling with SOFTMAX - use config values
    const int TOP_K = topK > 0 ? topK : 40;
    const float sampling_temp = temperature > 0 ? temperature : 0.7f;

    // Track recent tokens for repetition penalty
    std::vector<llama_token> recent_tokens;
    const int REPEAT_PENALTY_N = 32;

    while (g_generating && n_generated_tokens < maxTokens) {
        // Get logits
        float* logits = llama_get_logits(g_ctx);
        if (!logits) {
            __android_log_print(ANDROID_LOG_ERROR, "llama-jni", "ERROR: No logits!");
            break;
        }

        // Apply temperature to logits FIRST
        for (int i = 0; i < vocab_size; i++) {
            logits[i] /= sampling_temp;
        }

        // Apply repetition penalty to recently seen tokens
        if (repeatPenalty > 1.0f) {
            for (llama_token t : recent_tokens) {
                if (t >= 0 && t < vocab_size) {
                    logits[t] /= repeatPenalty;
                }
            }
        }

        // Compute softmax to get probabilities
        float max_logit = logits[0];
        for (int i = 1; i < vocab_size; i++) {
            if (logits[i] > max_logit) max_logit = logits[i];
        }

        float sum = 0.0f;
        for (int i = 0; i < vocab_size; i++) {
            logits[i] = expf(logits[i] - max_logit);  // Subtract max for numerical stability
            sum += logits[i];
        }

        // Normalize to get probabilities
        for (int i = 0; i < vocab_size; i++) {
            logits[i] /= sum;
        }

        // Now sample from top-k probabilities (cumulative distribution)
        // First find top-k and their cumulative probabilities
        std::vector<std::pair<int, float>> top_probs;  // token, probability
        for (int i = 0; i < vocab_size; i++) {
            top_probs.push_back({i, logits[i]});
        }

        // Sort by probability (highest first)
        std::partial_sort(top_probs.begin(), top_probs.begin() + TOP_K, top_probs.end(),
            [](const auto& a, const auto& b) { return a.second > b.second; });

        // Compute cumulative distribution for top-k
        float cum_sum = 0.0f;
        for (int i = 0; i < TOP_K && i < (int)top_probs.size(); i++) {
            cum_sum += top_probs[i].second;
            top_probs[i].second = cum_sum;
        }

        // Sample: pick random value in [0, cum_sum)
        float r = ((float)rand() / RAND_MAX) * cum_sum;

        // Find which bin r falls into
        llama_token token = top_probs[0].first;
        for (int i = 0; i < TOP_K && i < (int)top_probs.size(); i++) {
            if (r < top_probs[i].second) {
                token = top_probs[i].first;
                break;
            }
        }

        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: Manual softmax top-%d sample: token=%d, prob=%.4f",
            TOP_K, token, top_probs[0].second);

        // Check for EOS
        if (token == eos_token || token == llama_token_eot(g_model)) {
            __android_log_print(ANDROID_LOG_DEBUG, "llama-jni", "End of token (EOS/EOT) reached at token %d", n_generated_tokens);
            break;
        }

        // Get token text - use llama_token_to_piece to convert token to string
        char tokenBuffer[64];
        int tokenLen = llama_token_to_piece(g_model, token, tokenBuffer, sizeof(tokenBuffer), 0, false);
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
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "DEBUG: Token %d text: '%s'", n_generated_tokens, tokenBuffer);
        jstring tokenStr = env->NewStringUTF(tokenBuffer);
        env->CallVoidMethod(g_callback, onTokenMethod, tokenStr);
        env->DeleteLocalRef(tokenStr);

        // Accept the token
        llama_sampler_accept(g_sampler, token);

        // Track for repetition penalty
        recent_tokens.push_back(token);
        if (recent_tokens.size() > REPEAT_PENALTY_N) {
            recent_tokens.erase(recent_tokens.begin());
        }

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
        __android_log_print(ANDROID_LOG_DEBUG, "llama-jni", "Token %d: %.2f ms", n_generated_tokens, tok_ms);

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
