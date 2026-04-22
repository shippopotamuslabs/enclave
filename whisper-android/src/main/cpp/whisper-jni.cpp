#include <jni.h>
#include <algorithm>
#include <string>
#include <android/log.h>

#include "whisper.h"

namespace {

constexpr const char * LOG_TAG = "whisper-jni";

void throwIllegalState(JNIEnv * env, const char * message) {
    jclass exceptionClass = env->FindClass("java/lang/IllegalStateException");
    if (exceptionClass != nullptr) {
        env->ThrowNew(exceptionClass, message);
    }
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_browntowndev_pocketcrew_whisper_JniWhisperInference_initContext(
        JNIEnv * env,
        jclass,
        jstring modelPath) {
    if (modelPath == nullptr) {
        throwIllegalState(env, "Model path must not be null.");
        return 0L;
    }

    const char * pathChars = env->GetStringUTFChars(modelPath, nullptr);
    if (pathChars == nullptr) {
        throwIllegalState(env, "Unable to read model path.");
        return 0L;
    }

    whisper_context_params contextParams = whisper_context_default_params();
    whisper_context * context = whisper_init_from_file_with_params(pathChars, contextParams);
    env->ReleaseStringUTFChars(modelPath, pathChars);

    if (context == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to initialize Whisper context");
        return 0L;
    }

    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_browntowndev_pocketcrew_whisper_JniWhisperInference_processAudio(
        JNIEnv * env,
        jclass,
        jlong contextPtr,
        jfloatArray samples,
        jint numThreads) {
    auto * context = reinterpret_cast<whisper_context *>(contextPtr);
    if (context == nullptr) {
        throwIllegalState(env, "Whisper context is not initialized.");
        return env->NewStringUTF("");
    }

    if (samples == nullptr) {
        throwIllegalState(env, "Samples must not be null.");
        return env->NewStringUTF("");
    }

    const jsize sampleCount = env->GetArrayLength(samples);
    if (sampleCount <= 0) {
        return env->NewStringUTF("");
    }

    jfloat * sampleData = env->GetFloatArrayElements(samples, nullptr);
    if (sampleData == nullptr) {
        throwIllegalState(env, "Unable to read audio samples.");
        return env->NewStringUTF("");
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = "en";
    params.n_threads = std::max(1, static_cast<int>(numThreads));

    const int result = whisper_full(context, params, sampleData, sampleCount);
    env->ReleaseFloatArrayElements(samples, sampleData, JNI_ABORT);

    std::string transcript;
    if (result == 0) {
        const int segmentCount = whisper_full_n_segments(context);
        for (int index = 0; index < segmentCount; ++index) {
            const char * segmentText = whisper_full_get_segment_text(context, index);
            if (segmentText != nullptr) {
                transcript += segmentText;
            }
        }
    } else {
        __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Whisper transcription failed: %d", result);
    }

    return env->NewStringUTF(transcript.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_browntowndev_pocketcrew_whisper_JniWhisperInference_freeContext(
        JNIEnv *,
        jclass,
        jlong contextPtr) {
    auto * context = reinterpret_cast<whisper_context *>(contextPtr);
    if (context != nullptr) {
        whisper_free(context);
    }
}
