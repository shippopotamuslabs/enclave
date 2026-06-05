package com.browntowndev.enclave.localimagegen

/**
 * JNI surface for QNN (Hexagon NPU) and MNN (CPU) backends.
 *
 * Probe return codes: 0 = not packaged, 1 = packaged not ready, 2 = ready.
 * Generate return codes: 0 = success, negative = failure (see logcat).
 */
internal object LocalImageGenNative {
    init {
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
    ): Int
}
