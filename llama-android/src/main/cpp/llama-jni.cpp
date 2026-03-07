// JNI bridge for llama.cpp Android integration
// Provides thin JNI layer to expose llama.cpp functionality to Kotlin

#include <jni.h>
#include <string>
#include <memory>
#include <atomic>
#include <mutex>
#include <optional>
#include <vector>
#include <cstdlib>

#include "llama.h"

// Global state for the llama context - using raw pointers since llama_model/llama_context are opaque
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static llama_sampler* g_sampler = nullptr;
static std::atomic<bool> g_generating(false);
static std::mutex g_mutex;

// Callback interface
static jobject g_callback = nullptr;
static JNIEnv* g_callbackEnv = nullptr;

extern "C" {

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
    std::lock_guard<std::mutex> lock(g_mutex);

    // Unload existing model if any
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }

    std::string path = jstringToString(env, modelPath);

    // Model params
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = gpuLayers;
    mparams.use_mmap = true;

    // Load model
    g_model = llama_load_model_from_file(path.c_str(), mparams);
    if (!g_model) {
        return JNI_FALSE;
    }

    // Context params
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = contextSize;
    cparams.n_threads = threads;
    cparams.n_threads_batch = batchSize;

    // Create context
    g_ctx = llama_new_context_with_model(g_model, cparams);
    if (!g_ctx) {
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

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
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
}

JNIEXPORT void JNICALL
Java_com_browntowndev_pocketcrew_inference_llama_JniLlamaEngine_nativeClearKvCache(
    JNIEnv* env,
    jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) {
        llama_kv_cache_clear(g_ctx);
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

    std::string promptStr = jstringToString(env, prompt);

    // Initialize sampler with greedy sampling (we'll apply temperature via the sampler)
    g_sampler = llama_sampler_init_greedy();

    // Add temperature sampling
    llama_sampler* tempSampler = llama_sampler_init_temp(temperature);
    llama_sampler_chain_add(g_sampler, tempSampler);
    // Ownership transferred to chain

    g_generating = true;

    // Tokenize prompt
    std::vector<llama_token> tokens;
    tokens.resize(llama_tokenize(g_model, promptStr.c_str(), promptStr.size(), nullptr, 0, true, true));
    llama_tokenize(g_model, promptStr.c_str(), promptStr.size(), tokens.data(), tokens.size(), true, true);

    const int n_prompt_tokens = tokens.size();
    int n_generated_tokens = 0;

    // Callback methods
    jmethodID onTokenMethod = getCallbackMethod(env, g_callback, "onToken", "(Ljava/lang/String;)V");
    jmethodID onCompleteMethod = getCallbackMethod(env, g_callback, "onComplete", "(II)V");
    jmethodID onErrorMethod = getCallbackMethod(env, g_callback, "onError", "(Ljava/lang/String;)V");

    // Check if model context can hold prompt
    const int n_ctx = llama_n_ctx(g_ctx);
    if (n_prompt_tokens > n_ctx) {
        jstring errorMsg = env->NewStringUTF("Prompt too long for context");
        env->CallVoidMethod(g_callback, onErrorMethod, errorMsg);
        env->DeleteLocalRef(errorMsg);
        g_generating = false;
        return;
    }

    // Evaluate prompt
    llama_batch batch = llama_batch_get_one(tokens.data(), n_prompt_tokens, 0, 0);
    if (llama_decode(g_ctx, batch) != 0) {
        jstring errorMsg = env->NewStringUTF("Failed to decode prompt");
        env->CallVoidMethod(g_callback, onErrorMethod, errorMsg);
        env->DeleteLocalRef(errorMsg);
        g_generating = false;
        return;
    }

    // Sample and generate tokens
    while (g_generating && n_generated_tokens < maxTokens) {
        llama_token token = llama_sampler_sample(g_sampler, g_ctx, -1);

        if (token == llama_token_eos(g_model) || token == llama_token_eot(g_model)) {
            break;
        }

        // Get token text
        const char* tokenText = llama_token_get_text(g_model, token);
        jstring tokenStr = env->NewStringUTF(tokenText);
        env->CallVoidMethod(g_callback, onTokenMethod, tokenStr);
        env->DeleteLocalRef(tokenStr);

        // Accept the token
        llama_sampler_accept(g_sampler, token);

        // Evaluate token
        llama_batch tokenBatch = llama_batch_get_one(&token, 1, llama_n_ctx(g_ctx) + n_generated_tokens, 0);
        if (llama_decode(g_ctx, tokenBatch) != 0) {
            break;
        }

        n_generated_tokens++;
    }

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
}

} // extern "C"
