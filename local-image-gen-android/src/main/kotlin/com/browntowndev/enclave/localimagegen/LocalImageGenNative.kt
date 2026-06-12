package com.browntowndev.enclave.localimagegen

import android.util.Log

/**
 * JNI surface for the Enclave in-process diffusion engine (QNN + MNN CLIP).
 *
 * Probe return codes: 0 = not packaged, 1 = partial, 2 = ready.
 * Generate return codes: 0 = success, negative = failure (see logcat).
 */
internal object LocalImageGenNative {
    private const val TAG = "LocalImageGenNative"

    init {
        // Vendor lib only — do not bundle libcdsprpc.so in jniLibs (HIDL deps live in vendor ns).
        try {
            System.loadLibrary("cdsprpc")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load vendor libcdsprpc.so", e)
        }
        System.loadLibrary("local_image_gen_jni")
    }

    external fun nativeInit(
        nativeLibraryDir: String,
        qnnRuntimeDir: String,
    )

    external fun nativeProbeQnn(): Int

    external fun nativeProbeMnn(): Int

    external fun nativeGenerateQnn(
        modelDir: String,
        prompt: String,
        negativePrompt: String?,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        scheduler: String,
        seed: Long,
        outputPath: String,
        mode: Int,
        denoiseStrength: Float,
        initImagePath: String?,
        memoryPolicy: Int,
    ): Int

    external fun nativeGenerateMnn(
        modelDir: String,
        prompt: String,
        negativePrompt: String?,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        scheduler: String,
        seed: Long,
        outputPath: String,
        mode: Int,
        denoiseStrength: Float,
        initImagePath: String?,
        memoryPolicy: Int,
    ): Int

    external fun nativeCancel()

    /** Error text from the most recent failed generate call, or null after success. */
    external fun nativeGetLastGenerationError(): String?

    external fun nativeGetGenerationProgress(): IntArray?

    external fun nativeOnTrimMemory(level: Int)
}
