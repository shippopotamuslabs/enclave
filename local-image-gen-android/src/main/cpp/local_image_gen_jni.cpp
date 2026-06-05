#include <jni.h>
#include <android/log.h>

#include "local_image_gen_runtime.h"

#define LOG_TAG "local-image-gen-jni"

extern "C" int qnn_probe_runtime();
extern "C" int qnn_generate_image(
    const char* model_dir,
    const char* prompt,
    const char* negative_prompt,
    int width,
    int height,
    int steps,
    float cfg_scale,
    const char* scheduler,
    long seed,
    const char* output_path,
    char* error_out,
    int error_out_len);

extern "C" int mnn_probe_runtime();
extern "C" int mnn_generate_image(
    const char* model_dir,
    const char* prompt,
    const char* negative_prompt,
    int width,
    int height,
    int steps,
    float cfg_scale,
    const char* scheduler,
    long seed,
    const char* output_path,
    char* error_out,
    int error_out_len);

extern "C" JNIEXPORT void JNICALL
Java_com_browntowndev_enclave_localimagegen_LocalImageGenNative_nativeInit(
    JNIEnv* env,
    jclass,
    jstring native_lib_dir,
    jstring qnn_runtime_dir) {
    const char* native_c = env->GetStringUTFChars(native_lib_dir, nullptr);
    const char* runtime_c = env->GetStringUTFChars(qnn_runtime_dir, nullptr);
    local_image_gen::set_runtime_paths(native_c, runtime_c);
    env->ReleaseStringUTFChars(native_lib_dir, native_c);
    env->ReleaseStringUTFChars(qnn_runtime_dir, runtime_c);
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "nativeInit complete");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_browntowndev_enclave_localimagegen_LocalImageGenNative_nativeProbeQnn(
    JNIEnv*,
    jclass) {
    return qnn_probe_runtime();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_browntowndev_enclave_localimagegen_LocalImageGenNative_nativeProbeMnn(
    JNIEnv*,
    jclass) {
    return mnn_probe_runtime();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_browntowndev_enclave_localimagegen_LocalImageGenNative_nativeGenerateQnn(
    JNIEnv* env,
    jclass,
    jstring model_dir,
    jstring prompt,
    jstring negative_prompt,
    jint width,
    jint height,
    jint steps,
    jfloat cfg_scale,
    jstring scheduler,
    jlong seed,
    jstring output_path) {
    const char* model_dir_c = env->GetStringUTFChars(model_dir, nullptr);
    const char* prompt_c = env->GetStringUTFChars(prompt, nullptr);
    const char* negative_c = negative_prompt ? env->GetStringUTFChars(negative_prompt, nullptr) : "";
    const char* scheduler_c = scheduler ? env->GetStringUTFChars(scheduler, nullptr) : "dpm";
    const char* output_c = env->GetStringUTFChars(output_path, nullptr);
    char error[512] = {};
    const int code = qnn_generate_image(
        model_dir_c,
        prompt_c,
        negative_c,
        width,
        height,
        steps,
        cfg_scale,
        scheduler_c,
        seed,
        output_c,
        error,
        static_cast<int>(sizeof(error)));
    env->ReleaseStringUTFChars(model_dir, model_dir_c);
    env->ReleaseStringUTFChars(prompt, prompt_c);
    if (negative_prompt) {
        env->ReleaseStringUTFChars(negative_prompt, negative_c);
    }
    if (scheduler) {
        env->ReleaseStringUTFChars(scheduler, scheduler_c);
    }
    env->ReleaseStringUTFChars(output_path, output_c);
    if (code != 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "QNN generate failed: %s", error);
    }
    return code;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_browntowndev_enclave_localimagegen_LocalImageGenNative_nativeGenerateMnn(
    JNIEnv* env,
    jclass,
    jstring model_dir,
    jstring prompt,
    jstring negative_prompt,
    jint width,
    jint height,
    jint steps,
    jfloat cfg_scale,
    jstring scheduler,
    jlong seed,
    jstring output_path) {
    const char* model_dir_c = env->GetStringUTFChars(model_dir, nullptr);
    const char* prompt_c = env->GetStringUTFChars(prompt, nullptr);
    const char* negative_c = negative_prompt ? env->GetStringUTFChars(negative_prompt, nullptr) : "";
    const char* scheduler_c = scheduler ? env->GetStringUTFChars(scheduler, nullptr) : "dpm";
    const char* output_c = env->GetStringUTFChars(output_path, nullptr);
    char error[512] = {};
    const int code = mnn_generate_image(
        model_dir_c,
        prompt_c,
        negative_c,
        width,
        height,
        steps,
        cfg_scale,
        scheduler_c,
        seed,
        output_c,
        error,
        static_cast<int>(sizeof(error)));
    env->ReleaseStringUTFChars(model_dir, model_dir_c);
    env->ReleaseStringUTFChars(prompt, prompt_c);
    if (negative_prompt) {
        env->ReleaseStringUTFChars(negative_prompt, negative_c);
    }
    if (scheduler) {
        env->ReleaseStringUTFChars(scheduler, scheduler_c);
    }
    env->ReleaseStringUTFChars(output_path, output_c);
    if (code != 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "MNN generate failed: %s", error);
    }
    return code;
}