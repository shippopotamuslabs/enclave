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
#include <cstring>
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
static llama_batch* g_batch = nullptr;  // Points to s_batch
static llama_batch s_batch;  // The actual static batch - needs to be at file scope for unload to access
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

// Fallback prompt builder when no chat template is available
static std::string buildRawPrompt(const std::vector<llama_chat_message>& messages) {
    std::string prompt;
    for (const auto& msg : messages) {
        prompt += std::string(msg.role) + ": " + std::string(msg.content) + "\n";
    }
    prompt += "ASSISTANT:";
    return prompt;
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

    // Use the full context size from config
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Context size: requested=%d, using=%d", contextSize, contextSize);

    cparams.n_ctx = contextSize;

    // Use at least 4 threads for reasonable CPU performance
    // Single thread is extremely slow on mobile
    int actualThreads = (threads > 0) ? threads : 4;
    cparams.n_threads = actualThreads;
    cparams.n_threads_batch = actualThreads;

    // Use batch size from config parameter
    int actualBatchSize = (batchSize > 0) ? batchSize : 512;
    cparams.n_batch = actualBatchSize;
    cparams.n_ubatch = actualBatchSize;

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
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    // Create reusable batch (like official example)
    // Note: llama_batch_init returns llama_batch, not pointer
    // CRITICAL: If batch was previously used, free it first to avoid memory issues
    // (This handles the case where we're reloading a model after unload)
    if (s_batch.token != nullptr) {
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Freeing previous batch before reinit, was n_tokens=%d", s_batch.n_tokens);
        llama_batch_free(s_batch);
        // Reset to zero state
        s_batch = {};
    }
    // Initialize batch with capacity matching n_batch (2048) for stability
    // This avoids mismatch between batch capacity and n_batch
    // Note: Large prompts are now chunked automatically in nativeStartCompletion
    s_batch = llama_batch_init(2048, 0, 1);
    g_batch = &s_batch;
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Created reusable batch, n_tokens=%d, token=%p, logits=%p",
        s_batch.n_tokens, (void*)s_batch.token, (void*)s_batch.logits);

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

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== UNLOAD MODEL START ===");

    // Stop any ongoing generation first
    if (g_generating.exchange(false)) {
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Stopping generation before unload");
    }

    // CRITICAL: Clean up callback BEFORE freeing model/context
    // The callback holds a GlobalRef to the Kotlin callback object
    // If we free the model first, we may have dangling references
    if (g_callback) {
        env->DeleteGlobalRef(g_callback);
        g_callback = nullptr;
        g_callbackEnv = nullptr;
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Cleaned up callback reference");
    }

    if (g_sampler) {
        llama_sampler_free(g_sampler);
        g_sampler = nullptr;
    }

    // Batch cleanup: only call llama_batch_free, do NOT delete the pointer
    // because g_batch points to a static variable (s_batch)
    if (g_batch) {
        llama_batch_free(*g_batch);
        g_batch = nullptr;
        // CRITICAL: Also reset s_batch to prevent double-free on next load
        s_batch = {};
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Batch freed and static s_batch reset");
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
    // Reset the sampler to clear any accumulated state (penalties, etc.)
    if (g_sampler) {
        llama_sampler_reset(g_sampler);
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "KV cache clear - sampler reset");
    } else {
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "KV cache clear - no sampler to reset");
    }
}

JNIEXPORT void JNICALL
Java_com_browntowndev_pocketcrew_inference_llama_JniLlamaEngine_nativeStartCompletion(
    JNIEnv* env,
    jobject /* this */,
    jobjectArray roles,  // Array of role strings
    jobjectArray contents,  // Array of content strings
    jfloat temperature,
    jint topK,
    jfloat topP,
    jint maxTokens,
    jfloat repeatPenalty,
    jint reasoningBudget,
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

    // Parse chat messages from Java arrays (roles and contents)
    std::vector<llama_chat_message> chat_messages;
    jsize num_messages = env->GetArrayLength(roles);

    for (jsize i = 0; i < num_messages; i++) {
        jstring roleStr = (jstring)env->GetObjectArrayElement(roles, i);
        jstring contentStr = (jstring)env->GetObjectArrayElement(contents, i);

        std::string role = jstringToString(env, roleStr);
        std::string content = jstringToString(env, contentStr);

        // llama_chat_message takes ownership of the C strings
        char* roleCStr = new char[role.length() + 1];
        strcpy(roleCStr, role.c_str());
        char* contentCStr = new char[content.length() + 1];
        strcpy(contentCStr, content.c_str());

        chat_messages.push_back({roleCStr, contentCStr});

        env->DeleteLocalRef(roleStr);
        env->DeleteLocalRef(contentStr);
    }

    // Get chat template from model - llama.cpp reads from GGUF metadata automatically
    const char* tmpl = llama_model_chat_template(g_model, nullptr);
    std::string promptStr;

    if (tmpl != nullptr) {
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Using chat template from model: %s", tmpl);

        // First call to get required buffer size
        int32_t len = llama_chat_apply_template(tmpl, chat_messages.data(), chat_messages.size(), true, nullptr, 0);
        if (len < 0) {
            __android_log_print(ANDROID_LOG_WARN, "llama-jni", "llama_chat_apply_template failed (%d), falling back to raw prompt", len);
            // Fall back to building prompt manually
            promptStr = buildRawPrompt(chat_messages);
        } else {
            promptStr.resize(len);
            len = llama_chat_apply_template(tmpl, chat_messages.data(), chat_messages.size(), true, promptStr.data(), len);
            if (len < 0) {
                __android_log_print(ANDROID_LOG_WARN, "llama-jni", "llama_chat_apply_template (fill) failed (%d), falling back", len);
                promptStr = buildRawPrompt(chat_messages);
            }
        }
    } else {
        // No template in model - fall back to raw prompt building
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "No chat template in model, using raw prompt format");
        promptStr = buildRawPrompt(chat_messages);
    }

    // Free chat message strings
    for (auto& msg : chat_messages) {
        delete[] msg.role;
        delete[] msg.content;
    }

    // Initialize full sampler chain with all sampling parameters
    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());

    // Add temperature (default 0.7 if not set)
    float temp = temperature > 0 ? temperature : 0.7f;
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(temp));

    // Add top_k sampling (default 40 if > 0, otherwise no top_k)
    if (topK > 0) {
        llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(topK));
    }

    // Add top_p nucleus sampling (default 0.9 if > 0, otherwise no top_p)
    // Note: This API requires min_keep parameter (use 1 for minimum tokens)
    if (topP > 0) {
        llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(topP, 1));
    }

    // Add repeat penalty for token repetition (default 1.1 if > 0)
    // Note: Using llama_sampler_init_penalties which takes (last_n, repeat, freq, present)
    if (repeatPenalty > 0) {
        // penalty_last_n = 64, penalty_repeat = repeatPenalty, freq = 0, present = 0
        llama_sampler_chain_add(g_sampler, llama_sampler_init_penalties(64, repeatPenalty, 0.0f, 0.0f));
    }

    // Add greedy as head sampler for final selection
    llama_sampler_chain_add(g_sampler, llama_sampler_init_greedy());

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== SAMPLER CHAIN CONFIGURED ===");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Temperature: %.2f", temp);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Top-K: %d", topK);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Top-P: %.2f", topP);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Repeat penalty: %.2f", repeatPenalty);
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

    // Tokenize prompt - first call to get required buffer size
    // FIXED: For chat-templated prompts, the template already added special tokens (including BOS if needed)
    // So we set add_bos=false to avoid double-adding BOS
    // Use 8192 token buffer to handle large synthesis prompts
    std::vector<llama_token> tokens(8192);
    const int required_tokens = llama_tokenize(get_model_vocab(), promptStr.c_str(), promptStr.size(), tokens.data(), 8192, false, true);
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

    // Evaluate prompt
    auto prompt_start = std::chrono::high_resolution_clock::now();

    // Clear batch and validate
    llama_batch_clear(*g_batch);

    // DEFENSIVE: Check batch validity
    if (!g_batch || !g_batch->token || !g_batch->logits) {
        __android_log_print(ANDROID_LOG_ERROR, "llama-jni", "ERROR: Batch is invalid before prompt eval!");
        g_generating = false;
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Batch is invalid - model may not be properly loaded");
        return;
    }

    // Get the actual batch capacity from context
    const int n_batch = llama_n_batch(g_ctx);

    // Process prompt tokens in chunks - llama.cpp requires batch size <= n_batch
    int processed_tokens = 0;
    while (processed_tokens < n_prompt_tokens) {
        llama_batch_clear(*g_batch);

        // Calculate how many tokens in this chunk
        int chunk_size = std::min(n_batch, n_prompt_tokens - processed_tokens);

        // Add chunk of tokens to batch
        for (int i = 0; i < chunk_size; i++) {
            // Position continues from where we left off
            llama_batch_add(*g_batch, tokens[processed_tokens + i], processed_tokens + i, { 0 }, false);
        }

        // Only the last token of the entire prompt gets logits
        if (processed_tokens + chunk_size == n_prompt_tokens) {
            if (g_batch->logits) {
                g_batch->logits[g_batch->n_tokens - 1] = true;
            }
        }

        // Call llama_decode with this chunk
        int decode_result = llama_decode(g_ctx, *g_batch);
        if (decode_result != 0) {
            __android_log_print(ANDROID_LOG_ERROR, "llama-jni", "ERROR: llama_decode failed at token %d, result=%d", processed_tokens, decode_result);
            g_generating = false;
            env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Failed to decode prompt chunk");
            return;
        }

        processed_tokens += chunk_size;
    }

    auto prompt_end = std::chrono::high_resolution_clock::now();
    double prompt_ms = std::chrono::duration<double, std::milli>(prompt_end - prompt_start).count();
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Prompt evaluation took %.2f ms (%.2f ms/token)",
        prompt_ms, prompt_ms / n_prompt_tokens);

    // Keep the batch for reuse in generation loop

    // Sample and generate tokens
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== GENERATING TOKENS ===");

    // Verify sampler is valid
    if (!g_sampler) {
        __android_log_print(ANDROID_LOG_ERROR, "llama-jni", "ERROR: sampler is NULL!");
        g_generating = false;
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Sampler is NULL");
        return;
    }

    auto gen_start = std::chrono::high_resolution_clock::now();

    // Get special tokens and vocab
    llama_token eos_token = llama_vocab_eos(get_model_vocab());
    const int vocab_size = llama_vocab_n_tokens(get_model_vocab());

    // Generation loop using official llama.cpp sampler
    while (g_generating && n_generated_tokens < maxTokens) {
        // Verify logits are available before sampling
        float* logits = llama_get_logits(g_ctx);
        if (!logits) {
            __android_log_print(ANDROID_LOG_ERROR, "llama-jni", "ERROR: No logits available before sampling!");
            break;
        }

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

        // FIXED: Calculate position BEFORE incrementing n_generated_tokens
        // The first generated token goes at position n_prompt_tokens (not n_prompt_tokens + 1)
        int32_t current_pos = n_prompt_tokens + n_generated_tokens;

        // Get token text - use llama_token_to_piece to convert token to string
        // FIXED: Use larger buffer (256 bytes) to handle any token
        char tokenBuffer[256];
        int tokenLen = llama_token_to_piece(get_model_vocab(), token, tokenBuffer, sizeof(tokenBuffer) - 1, 0, false);
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

        // Log first generated token for debugging
        if (n_generated_tokens == 0) {
            const char* first_token_text = llama_vocab_get_text(get_model_vocab(), token);
            __android_log_print(ANDROID_LOG_INFO, "llama-jni", "FIRST TOKEN: id=%d, text='%s', pos=%d",
                token, first_token_text ? first_token_text : "(null)", current_pos);
            if (first_token_text && strstr(first_token_text, "<think>") != nullptr) {
                __android_log_print(ANDROID_LOG_INFO, "llama-jni", "THINK START TOKEN DETECTED!");
            }
        }

        // Create a fresh batch for each token
        llama_batch_clear(*g_batch);

        // DEFENSIVE: Verify batch is valid before use
        if (!g_batch || !g_batch->token || !g_batch->logits) {
            __android_log_print(ANDROID_LOG_ERROR, "llama-jni", "ERROR: Batch is invalid in generation loop!");
            break;
        }

        // Add the token to batch at the correct position
        llama_batch_add(*g_batch, token, current_pos, { 0 }, true);

        // Verify context is still valid
        if (!g_ctx) {
            __android_log_print(ANDROID_LOG_ERROR, "llama-jni", "ERROR: Context is null!");
            break;
        }

        // FIXED: Decode BEFORE incrementing the counter
        int decode_result = llama_decode(g_ctx, *g_batch);
        if (decode_result != 0) {
            __android_log_print(ANDROID_LOG_ERROR, "llama-jni", "ERROR: llama_decode failed with code %d at pos %d", decode_result, current_pos);
            break;
        }

        // FIXED: Increment counter AFTER successful decode
        n_generated_tokens++;
        if (n_generated_tokens % 100 == 0) {
            __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Generated %d tokens so far...", n_generated_tokens);
        }
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
