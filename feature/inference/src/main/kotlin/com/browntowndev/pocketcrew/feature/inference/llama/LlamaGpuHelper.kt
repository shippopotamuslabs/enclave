package com.browntowndev.pocketcrew.feature.inference.llama

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LlamaGpuHelper"

/**
 * Helper class to determine if GPU acceleration is available for llama.cpp inference.
 *
 * GPU acceleration via ggml-metal is only available on:
 * - Devices with a GPU that supports compute shaders
 * - API 24+ (Android 7.0+)
 *
 * On emulators and devices without proper GPU support, we fall back to CPU-only mode.
 */
object LlamaGpuHelper {

    /**
     * Check if GPU acceleration is likely available on this device.
     *
     * This is a heuristic check - it may return true even on devices where
     * GPU acceleration doesn't work well. The llama.cpp library may still
     * fail with GPU, in which case the caller should handle the exception
     * and fall back to CPU mode.
     */
    fun isGpuAvailable(context: Context): Boolean {
        // Emulators generally don't support GPU compute well
        if (isEmulator()) {
            return false
        }

        // Check if device has GPU memory (rough heuristic)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        if (activityManager != null) {
            val memInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)

            // Require at least 3GB RAM for GPU inference
            if (memInfo.totalMem < 3L * 1024 * 1024 * 1024) {
                return false
            }
        }

        // Could also check for specific GPU features via OpenGL ES,
        // but that requires a surface context. For now, assume
        // most modern devices with enough RAM can handle GPU.

        return true
    }

    /**
     * Dynamically calculates the optimal number of GPU layers to offload based on
     * real-time available memory, OS safety buffers, and the specific model's size.
     *
     * @param context Android context to access ActivityManager
     * @param modelSizeBytes The exact file size of the .gguf model
     * @param totalModelLayers The total number of layers in the model architecture (e.g., 32 for Llama 3 8B)
     * @param contextSizeBytes Estimated size of the KV cache (default: ~500MB)
     * @return The optimal number of GPU layers (0 for CPU-only mode)
     */
    fun calculateDynamicGpuLayers(
        context: Context,
        modelSizeBytes: Long,
        totalModelLayers: Int,
        contextSizeBytes: Long = 500L * 1024 * 1024
    ): Int {
        try {
            // 1. Get real-time available memory from the Android OS
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager == null) {
                Log.w(TAG, "ActivityManager not available, falling back to CPU")
                return 0
            }

            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)

            val availableRamBytes = memoryInfo.availMem

            // 2. Reserve a strict safety buffer for Android OS and your app's UI to prevent crashing
            val osHeadroomBytes = 1536L * 1024 * 1024 // 1.5 GB safety buffer

            // 3. Calculate how much RAM we can actually use for the GPU
            val usableRamBytes = availableRamBytes - osHeadroomBytes - contextSizeBytes

            if (usableRamBytes <= 0) {
                Log.w(TAG, "Not enough available RAM for GPU offloading. Falling back to CPU.")
                return 0
            }

            // Handle edge cases to avoid division by zero
            if (modelSizeBytes <= 0 || totalModelLayers <= 0) {
                Log.w(TAG, "Invalid model parameters, falling back to CPU")
                return 0
            }

            // 4. Estimate how much memory a single layer of this specific model consumes
            val bytesPerLayer = modelSizeBytes / totalModelLayers.coerceAtLeast(1)

            // 5. Calculate how many layers fit into our usable RAM
            val optimalLayers = (usableRamBytes / bytesPerLayer).toInt()

            // 6. Ensure we don't return a negative number or exceed the model's max layers
            val finalLayerCount = optimalLayers.coerceIn(0, totalModelLayers)

            Log.i(TAG, "Available RAM: ${availableRamBytes / 1024 / 1024}MB. Offloading $finalLayerCount/$totalModelLayers layers.")

            return finalLayerCount

        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate dynamic layers, using safe fallback.", e)
            return 0 // Safest fallback is CPU-only
        }
    }

    /**
     * Check if running on an Android emulator.
     */
    private fun isEmulator(): Boolean {
        return (
            Build.BRAND.equals("android", ignoreCase = true) ||
            Build.MANUFACTURER.equals("android", ignoreCase = true) ||
            Build.HARDWARE.startsWith("goldfish") ||
            Build.HARDWARE.startsWith("ranchu") ||
            Build.HARDWARE.contains("vbox86") ||
            Build.MODEL.contains("sdk_gphone") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("AVD") ||
            Build.DEVICE.startsWith("sdk_") ||
            Build.DEVICE.startsWith("vbox86") ||
            Build.PRODUCT.startsWith("sdk_") ||
            Build.PRODUCT.startsWith("vbox86")
        )
    }
}

/**
 * Configuration for llama.cpp GPU settings.
 */
data class GpuConfig(
    val gpuLayers: Int,
    val useGpu: Boolean
) {
    companion object {

        /**
         * Get recommended GPU configuration with specific model parameters.
         * This uses dynamic calculation based on real-time available memory.
         *
         * @param context Android context
         * @param modelSizeBytes The exact size of the model file in bytes
         * @param totalModelLayers The total number of layers in the model
         * @param contextSizeBytes Estimated KV cache size (default: 500MB)
         */
        fun forDevice(
            context: Context,
            modelSizeBytes: Long,
            totalModelLayers: Int,
            contextSizeBytes: Long = 500L * 1024 * 1024
        ): GpuConfig {
            Log.i(TAG, "=== GPU CONFIG DETECTION (Dynamic) ===")
            Log.i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            Log.i(TAG, "Hardware: ${Build.HARDWARE}")
            Log.i(TAG, "API Level: ${Build.VERSION.SDK_INT}")
            Log.i(TAG, "Model size: ${modelSizeBytes / 1024 / 1024}MB")
            Log.i(TAG, "Model layers: $totalModelLayers")

            // Check emulator inline
            val isEmulator = (
                Build.BRAND.equals("android", ignoreCase = true) ||
                Build.MANUFACTURER.equals("android", ignoreCase = true) ||
                Build.HARDWARE.startsWith("goldfish") ||
                Build.HARDWARE.startsWith("ranchu") ||
                Build.HARDWARE.contains("vbox86") ||
                Build.MODEL.contains("sdk_gphone") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("AVD") ||
                Build.DEVICE.startsWith("sdk_") ||
                Build.DEVICE.startsWith("vbox86") ||
                Build.PRODUCT.startsWith("sdk_") ||
                Build.PRODUCT.startsWith("vbox86")
            )
            val hasEnoughRam = checkRam(context)

            Log.i(TAG, "Is emulator: $isEmulator")
            Log.i(TAG, "Has enough RAM (3GB+): $hasEnoughRam")

            val hasGpu = LlamaGpuHelper.isGpuAvailable(context)
            val calculatedGpuLayers = LlamaGpuHelper.calculateDynamicGpuLayers(
                context = context,
                modelSizeBytes = modelSizeBytes,
                totalModelLayers = totalModelLayers,
                contextSizeBytes = contextSizeBytes
            )

            Log.i(TAG, "GPU available (heuristic): $hasGpu")
            Log.i(TAG, "Calculated GPU layers (dynamic): $calculatedGpuLayers")
            Log.i(TAG, "=====================================")

            return if (hasGpu && calculatedGpuLayers > 0) {
                Log.i(TAG, "GPU config: gpuLayers=$calculatedGpuLayers, useGpu=true")
                GpuConfig(
                    gpuLayers = calculatedGpuLayers,
                    useGpu = true
                )
            } else {
                Log.i(TAG, "GPU config: gpuLayers=0, useGpu=false (CPU only)")
                GpuConfig(
                    gpuLayers = 0,
                    useGpu = false
                )
            }
        }

        private fun checkRam(context: Context): Boolean {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager != null) {
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                return memInfo.totalMem >= 3L * 1024 * 1024 * 1024
            }
            return false
        }

        /**
         * Get the total RAM in GB for dynamic GPU layer calculation.
         */
        private fun getTotalRamGb(context: Context): Int {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (activityManager != null) {
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                return (memInfo.totalMem / (1024 * 1024 * 1024)).toInt()
            }
            return 0
        }
    }
}
