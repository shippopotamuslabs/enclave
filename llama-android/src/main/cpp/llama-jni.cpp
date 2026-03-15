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
#include "ggml-backend.h"

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

        // Set LD_LIBRARY_PATH for OpenCL to find vendor libraries
        // This is needed on Android to locate the Mali/OpenCL driver
        const char* vendor_lib_path = "/vendor/lib64:/vendor/lib";
        const char* existing_path = getenv("LD_LIBRARY_PATH");
        if (existing_path) {
            char new_path[1024];
            snprintf(new_path, sizeof(new_path), "%s:%s", vendor_lib_path, existing_path);
            setenv("LD_LIBRARY_PATH", new_path, 1);
            __android_log_print(ANDROID_LOG_INFO, "llama-jni", "LD_LIBRARY_PATH set to: %s", new_path);
        } else {
            setenv("LD_LIBRARY_PATH", vendor_lib_path, 1);
            __android_log_print(ANDROID_LOG_INFO, "llama-jni", "LD_LIBRARY_PATH set to: %s", vendor_lib_path);
        }

        llama_backend_init();
        g_initialized = true;

        // Check GPU offload support
        bool supports_gpu = llama_supports_gpu_offload();

        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== AVAILABLE BACKENDS ===");
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "GPU offload support (runtime check): %s", supports_gpu ? "YES" : "NO");
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "===========================");

        // Note: SVE and other CPU feature detection has been moved to ggml.h
        if (gpuLayers > 0) {
            __android_log_print(ANDROID_LOG_INFO, "llama-jni", "GPU mode requested with %d layers", gpuLayers);
        } else {
            __android_log_print(ANDROID_LOG_INFO, "llama-jni", "CPU-only mode requested (gpuLayers=0)");
        }
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

    // Clear the KV cache memory
    if (g_ctx) {
        llama_memory_clear(llama_get_memory(g_ctx), true);
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "KV cache cleared");
    }

    // Reset the sampler to clear any accumulated state (penalties, etc.)
    if (g_sampler) {
        llama_sampler_reset(g_sampler);
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Sampler reset");
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
    jfloat minP,
    jint maxTokens,
    jfloat repeatPenalty,
    jfloat penaltyFreq,
    jfloat penaltyPresent,
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

    // Clear KV cache between conversation turns to ensure clean state
    // This prevents corruption from leftover state from previous completions
    llama_memory_clear(llama_get_memory(g_ctx), true);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Cleared KV cache for new completion");

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
    const char* tmpl_raw = llama_model_chat_template(g_model, nullptr);
    std::string promptStr;
    bool template_applied = false;

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== CHAT TEMPLATE (Unsloth LFM2.5 check) ===");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Template from GGUF: %s", tmpl_raw ? tmpl_raw : "(null)");

    // Try the metadata template first
    if (tmpl_raw != nullptr) {
        int32_t len = llama_chat_apply_template(tmpl_raw, chat_messages.data(), chat_messages.size(), true, nullptr, 0);
        if (len > 0) {
            promptStr.resize(len);
            int32_t filled = llama_chat_apply_template(tmpl_raw, chat_messages.data(), chat_messages.size(), true, promptStr.data(), len);
            if (filled == len && filled > 0) {
                template_applied = true;
                __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Template from GGUF applied successfully (%d chars)", len);
            } else {
                __android_log_print(ANDROID_LOG_WARN, "llama-jni", "GGUF template sizing ok but fill failed (%d vs %d)", filled, len);
            }
        } else {
            __android_log_print(ANDROID_LOG_WARN, "llama-jni", "GGUF template apply failed (len=%d)", len);
        }
    }

    // Fallback chain if metadata template didn't work
    if (!template_applied) {
        // Try well-known built-in templates that don't require Jinja parsing
        const char* fallback_names[] = {"chatml", "llama3", "llama2", "mistral-v1", "gemma", nullptr};

        for (int i = 0; fallback_names[i] != nullptr; ++i) {
            const char* name = fallback_names[i];
            const char* tmpl_fallback = llama_model_chat_template(g_model, name);

            if (tmpl_fallback && strlen(tmpl_fallback) > 10) {
                int32_t len = llama_chat_apply_template(tmpl_fallback, chat_messages.data(), chat_messages.size(), true, nullptr, 0);
                if (len > 0) {
                    promptStr.resize(len);
                    int32_t filled = llama_chat_apply_template(tmpl_fallback, chat_messages.data(), chat_messages.size(), true, promptStr.data(), len);
                    if (filled == len && filled > 0) {
                        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Fallback template '%s' applied (%d chars)", name, len);
                        template_applied = true;
                        break;
                    }
                }
            }
        }

        // Ultimate fallback - try ChatML directly since LFM2.5 uses ChatML-like format
        if (!template_applied) {
            __android_log_print(ANDROID_LOG_WARN, "llama-jni", "Trying ChatML format as final fallback");
            // Manual ChatML format - use double newlines for proper word boundaries
            promptStr = "<|startoftext|>";
            for (const auto& msg : chat_messages) {
                promptStr += "<|im_start|>" + std::string(msg.role) + "\n" + std::string(msg.content) + "<|im_end|>\n\n";
            }
            promptStr += "<|im_start|>assistant\n";
            template_applied = true;
            __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Manual ChatML format applied");
        }
    }

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Final prompt length: %zu chars", promptStr.size());

    // Free chat message strings
    for (auto& msg : chat_messages) {
        delete[] msg.role;
        delete[] msg.content;
    }

    // Initialize full sampler chain with all sampling parameters
    g_sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());

    // Temperature
    float temp = temperature > 0 ? temperature : 0.7f;
    llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(temp));

    // Top-K
    if (topK > 0) {
        llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(topK));
    }

    // Top-P
    if (topP > 0) {
        llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(topP, 1));
    }

    // Min-P
    llama_sampler_chain_add(g_sampler, llama_sampler_init_min_p(minP, 1));

    // Repeat penalties
    float penalty_repeat = (repeatPenalty > 0) ? repeatPenalty : 1.10f;
    float penalty_freq = (penaltyFreq > 0.0f) ? penaltyFreq : 0.05f;
    float penalty_present = (penaltyPresent > 0.0f) ? penaltyPresent : 0.05f;
    int   penalty_last_n = 128;
    llama_sampler_chain_add(g_sampler, llama_sampler_init_penalties(penalty_last_n, penalty_repeat, penalty_freq, penalty_present));

    // Final sampler: use dist for probabilistic sampling (temp > 0), greedy for deterministic (temp = 0)
    if (temp > 0.0f) {
        llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    } else {
        llama_sampler_chain_add(g_sampler, llama_sampler_init_greedy());
    }

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "=== SAMPLER CONFIGURED ===");
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Penalties: repeat=%.2f, freq=%.2f, present=%.2f", penalty_repeat, penalty_freq, penalty_present);
    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Top-K: %d | Top-P: %.2f | Min-P: 0.05 | Temp: %.2f", topK, topP, temp);
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
    // Note: accumulated_output removed - no longer needed after fixing chat template
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

        // Check for standard stop tokens (EOS/EOT)
        if (token == eos_token || token == llama_vocab_eot(get_model_vocab())) {
            __android_log_print(ANDROID_LOG_DEBUG, "llama-jni", "Stop token (EOS/EOT) reached at token %d", n_generated_tokens);
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

        // Send token to callback - let JNI handle any invalid UTF-8 gracefully
        jstring tokenStr = env->NewStringUTF(tokenBuffer);
        env->CallVoidMethod(g_callback, onTokenMethod, tokenStr);
        env->DeleteLocalRef(tokenStr);

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

// Get current context size (n_ctx from config)
JNIEXPORT jint JNICALL
Java_com_browntowndev_pocketcrew_inference_llama_JniLlamaEngine_nativeGetContextSize(
    JNIEnv* env,
    jobject /* this */
) {
    if (!g_ctx) {
        return 0;
    }
    return llama_n_ctx(g_ctx);
}

// Get current KV cache token count (positions used)
// Note: This returns the sum of prompt + generated tokens as a proxy for context usage
// since llama_memory_get_num_cells_used is not available in this llama.cpp version
JNIEXPORT jint JNICALL
Java_com_browntowndev_pocketcrew_inference_llama_JniLlamaEngine_nativeGetContextUsage(
    JNIEnv* env,
    jobject /* this */
) {
    // We cannot directly get the KV cache cell count in this llama.cpp version
    // Return 0 to indicate we can't track usage directly
    // The Kotlin side will track token counts separately
    if (!g_ctx) {
        return 0;
    }
    // Return the maximum context size as a placeholder
    // The actual usage tracking happens in Kotlin via token counts
    return llama_n_ctx(g_ctx);
}

// Compress context using position division
// factor: divisor for positions (e.g., 2 halves the context window)
// Returns true if compression was successful, false otherwise
JNIEXPORT jboolean JNICALL
Java_com_browntowndev_pocketcrew_inference_llama_JniLlamaEngine_nativeCompressContext(
    JNIEnv* env,
    jobject /* this */,
    jint seqId,
    jint factor
) {
    if (!g_ctx) {
        __android_log_print(ANDROID_LOG_WARN, "llama-jni", "Compress context: no context");
        return JNI_FALSE;
    }

    llama_memory_t mem = llama_get_memory(g_ctx);
    if (!mem) {
        __android_log_print(ANDROID_LOG_WARN, "llama-jni", "Compress context: no memory");
        return JNI_FALSE;
    }

    // llama_memory_seq_div divides all positions in the sequence by the factor
    // This effectively compresses the context window
    // seqId = 0 means apply to all sequences
    // p0 = -1 means from the beginning
    // p1 = -1 means to the end
    llama_memory_seq_div(mem, seqId, -1, -1, factor);

    __android_log_print(ANDROID_LOG_INFO, "llama-jni", "Context compressed by factor %d", factor);

    return JNI_TRUE;
}

// Get state size for save/load (in bytes)
JNIEXPORT jint JNICALL
Java_com_browntowndev_pocketcrew_inference_llama_JniLlamaEngine_nativeGetStateSize(
    JNIEnv* env,
    jobject /* this */
) {
    if (!g_ctx) {
        return 0;
    }
    return (jint)llama_state_get_size(g_ctx);
}

// Save state to byte array
JNIEXPORT jbyteArray JNICALL
Java_com_browntowndev_pocketcrew_inference_llama_JniLlamaEngine_nativeGetState(
    JNIEnv* env,
    jobject /* this */
) {
    if (!g_ctx) {
        return nullptr;
    }

    size_t size = llama_state_get_size(g_ctx);
    if (size == 0) {
        return nullptr;
    }

    std::vector<uint8_t> state(size);
    size_t written = llama_state_get_data(g_ctx, state.data(), size);

    if (written == 0) {
        __android_log_print(ANDROID_LOG_ERROR, "llama-jni", "Failed to get state data");
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(written);
    if (result) {
        env->SetByteArrayRegion(result, 0, written, (jbyte*)state.data());
    }

    return result;
}

// Load state from byte array
JNIEXPORT jboolean JNICALL
Java_com_browntowndev_pocketcrew_inference_llama_JniLlamaEngine_nativeSetState(
    JNIEnv* env,
    jobject /* this */,
    jbyteArray stateArray
) {
    if (!g_ctx || !stateArray) {
        return JNI_FALSE;
    }

    jsize size = env->GetArrayLength(stateArray);
    if (size == 0) {
        return JNI_FALSE;
    }

    jbyte* bytes = env->GetByteArrayElements(stateArray, nullptr);
    if (!bytes) {
        return JNI_FALSE;
    }

    bool ok = llama_state_set_data(g_ctx, (uint8_t*)bytes, size);

    env->ReleaseByteArrayElements(stateArray, bytes, JNI_ABORT);

    if (ok) {
        __android_log_print(ANDROID_LOG_INFO, "llama-jni", "State restored successfully (%d bytes)", size);
    } else {
        __android_log_print(ANDROID_LOG_ERROR, "llama-jni", "Failed to restore state");
    }

    return ok ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
