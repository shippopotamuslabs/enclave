package com.browntowndev.pocketcrew.inference.llama

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
         * Get recommended GPU configuration for the current device.
         */
        fun forDevice(context: Context, modelSizeMb: Int = 0): GpuConfig {
            Log.i(TAG, "=== GPU CONFIG DETECTION ===")
            Log.i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            Log.i(TAG, "Hardware: ${Build.HARDWARE}")
            Log.i(TAG, "API Level: ${Build.VERSION.SDK_INT}")

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
            val totalRamGb = getTotalRamGb(context)
            val calculatedGpuLayers = calculateGpuLayers(totalRamGb)

            Log.i(TAG, "GPU available (heuristic): $hasGpu")
            Log.i(TAG, "Total RAM: ${totalRamGb}GB")
            Log.i(TAG, "Calculated GPU layers: $calculatedGpuLayers")
            Log.i(TAG, "==========================")

            return if (hasGpu && calculatedGpuLayers > 0) {
                // GPU enabled based on device capabilities
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
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            if (activityManager != null) {
                val memInfo = android.app.ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                return memInfo.totalMem >= 3L * 1024 * 1024 * 1024
            }
            return false
        }

        /**
         * Get the total RAM in GB for dynamic GPU layer calculation.
         */
        private fun getTotalRamGb(context: Context): Int {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            if (activityManager != null) {
                val memInfo = android.app.ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                return (memInfo.totalMem / (1024 * 1024 * 1024)).toInt()
            }
            return 0
        }

        /**
         * Calculate optimal GPU layers based on available RAM.
         * - 3GB RAM: 0 layers (CPU only, not enough for GPU)
         * - 4GB RAM: 8 layers
         * - 6GB RAM: 16 layers
         * - 8GB+ RAM: 24 layers
         */
        private fun calculateGpuLayers(totalRamGb: Int): Int {
            return when {
                totalRamGb < 4 -> 0  // Not enough RAM for GPU inference
                totalRamGb < 6 -> 8
                totalRamGb < 8 -> 16
                else -> 24
            }
        }
    }
}
