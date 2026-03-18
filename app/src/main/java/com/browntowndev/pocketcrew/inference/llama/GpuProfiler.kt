package com.browntowndev.pocketcrew.inference.llama

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GpuProfiler"

/**
 * Backend type enumeration - must match C++ enum in llama-jni.cpp
 */
enum class LlamaBackend(val value: Int) {
    CPU(0),
    OPENCL(1),
    VULKAN(2);

    companion object {
        fun fromValue(value: Int): LlamaBackend = entries.first { it.value == value }
    }
}

/**
 * Exhaustive GPU hardware profiler for llama.cpp backend selection.
 *
 * This class performs runtime detection of the device's GPU and routes to the optimal
 * backend based on known driver issues and performance characteristics:
 *
 * - Qualcomm Adreno: Force OpenCL (Vulkan is broken - driver hangs, 1GB limit, shader crashes)
 * - ARM Mali/Immortalis: Force CPU (Vulkan driver hangs, crashes on compute shaders)
 * - Samsung Xclipse (RDNA-based): Allow Vulkan (AMD-based Vulkan stack works well)
 *
 * Detection uses EGL context to query GL_RENDERER string, which is more reliable
 * than Build.HARDWARE which may not contain the exact GPU model.
 */
@Singleton
class GpuProfiler @Inject constructor() {

    companion object {
        // GPU patterns - enable Vulkan for modern Adreno GPUs
        // Adreno 6xx series and newer support Vulkan well
        private val ADRENO_PATTERNS = listOf(
            "Adreno 7" to LlamaBackend.VULKAN,  // Adreno 730+ (Snapdragon 8 Gen 1+)
            "Adreno 6" to LlamaBackend.VULKAN,  // Adreno 600-690 (Snapdragon 888, 778G, etc)
        )

        // Mali - enable Vulkan for modern GPUs
        private val MALI_PATTERNS = listOf(
            "Mali-G7" to LlamaBackend.VULKAN,
            "Mali-G6" to LlamaBackend.VULKAN,
            "Mali-G5" to LlamaBackend.VULKAN,
            "Immortalis-G7" to LlamaBackend.VULKAN,
            "Immortalis" to LlamaBackend.VULKAN,
        )

        private val XCLIPSE_PATTERNS = listOf(
            "Xclipse" to LlamaBackend.VULKAN,  // Samsung Xclipse (RDNA2)
        )

        // Default to CPU for unknown GPUs
        private val DEFAULT_BACKEND = LlamaBackend.CPU
    }

    // Cached GPU name to avoid re-querying
    private var cachedGpuName: String? = null

    /**
     * Detect the optimal backend for the current device's GPU.
     *
     * @return The recommended LlamaBackend for this device
     */
    fun detectOptimalBackend(): LlamaBackend {
        val gpuName = detectGpuName()
        Log.i(TAG, "Detected GPU: $gpuName")

        // Check Adreno first (most common)
        for ((pattern, backend) in ADRENO_PATTERNS) {
            if (gpuName.contains(pattern, ignoreCase = true)) {
                Log.i(TAG, "Matched Adreno pattern '$pattern' -> Backend: $backend")
                return backend
            }
        }

        // Check Mali/Immortalis
        for ((pattern, backend) in MALI_PATTERNS) {
            if (gpuName.contains(pattern, ignoreCase = true)) {
                Log.i(TAG, "Matched Mali/Immortalis pattern '$pattern' -> Backend: $backend")
                return backend
            }
        }

        // Check Xclipse (Samsung RDNA)
        for ((pattern, backend) in XCLIPSE_PATTERNS) {
            if (gpuName.contains(pattern, ignoreCase = true)) {
                Log.i(TAG, "Matched Xclipse/RDNA pattern '$pattern' -> Backend: $backend")
                return backend
            }
        }

        // Default fallback - try Vulkan with CPU fallback
        Log.w(TAG, "Unknown GPU '$gpuName', defaulting to: $DEFAULT_BACKEND")
        return DEFAULT_BACKEND
    }

    /**
     * Detect the GPU name using EGL context.
     *
     * This is more reliable than Build.HARDWARE as it queries the actual
     * GPU driver for the renderer string.
     */
    fun detectGpuName(): String {
        cachedGpuName?.let { return it }

        val gpuName = try {
            queryGpuViaEgl()
        } catch (e: Exception) {
            Log.w(TAG, "EGL query failed, falling back to Build.HARDWARE", e)
            fallbackDetectGpuName()
        }

        cachedGpuName = gpuName
        return gpuName
    }

    /**
     * Query GPU name via EGL - this is the most reliable method.
     */
    private fun queryGpuViaEgl(): String {
        // Get EGL display
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL display")
        }

        // Initialize EGL
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL")
        }

        try {
            // EGL constants (hardcoded for compatibility)
            val EGL_OPENGL_ES2_BIT = 4
            val EGL_CONTEXT_CLIENT_VERSION = 0x3098

            // Configure EGL for OpenGL ES 2.0
            val configAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )

            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)) {
                throw RuntimeException("Unable to choose EGL config")
            }

            val config = configs[0] ?: throw RuntimeException("No EGL config available")

            // Create context
            val contextAttribs = intArrayOf(
                EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )

            val context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0
            )

            if (context == EGL14.EGL_NO_CONTEXT) {
                throw RuntimeException("Unable to create EGL context")
            }

            try {
                // Create pbuffer surface
                val surfaceAttribs = intArrayOf(
                    EGL14.EGL_WIDTH, 1,
                    EGL14.EGL_HEIGHT, 1,
                    EGL14.EGL_NONE
                )

                val surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)
                if (surface == EGL14.EGL_NO_SURFACE) {
                    throw RuntimeException("Unable to create pbuffer surface")
                }

                try {
                    // Make current and query renderer
                    if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                        throw RuntimeException("Unable to make EGL context current")
                    }

                    val renderer = ByteArray(256)
                    GLES20.glGetString(GLES20.GL_RENDERER)?.let {
                        val bytes = it.toByteArray()
                        System.arraycopy(bytes, 0, renderer, 0, bytes.size.coerceAtMost(256))
                    }

                    val gpuName = String(renderer).trim { it.code == 0 }
                    Log.i(TAG, "EGL detected GPU: $gpuName")

                    return gpuName.ifEmpty { fallbackDetectGpuName() }
                } finally {
                    EGL14.eglDestroySurface(display, surface)
                }
            } finally {
                EGL14.eglDestroyContext(display, context)
            }
        } finally {
            EGL14.eglTerminate(display)
        }
    }

    /**
     * Fallback detection using Build.HARDWARE and Build.SOC_MODEL
     */
    private fun fallbackDetectGpuName(): String {
        val hardware = Build.HARDWARE.lowercase()
        val socModel = Build.SOC_MODEL.lowercase()

        Log.i(TAG, "Fallback detection - Hardware: $hardware, SoC: $socModel")

        return when {
            // Check for Qualcomm
            hardware.contains("qcom") || hardware.contains("snapdragon") -> "Qualcomm Adreno (fallback)"
            // Check for Samsung
            hardware.contains("exynos") -> "Samsung Exynos (fallback)"
            // Check for MediaTek
            hardware.contains("mt") || socModel.contains("dimensity") -> "MediaTek Dimensity (fallback)"
            // Check for Google Tensor
            hardware.contains("tensor") -> "Google Tensor (fallback)"
            // Check for Huawei
            hardware.contains("kirin") -> "HiSilicon Kirin (fallback)"
            // Default
            else -> "Unknown GPU (${Build.HARDWARE})"
        }
    }

    /**
     * Check if the device is an emulator (for debugging)
     */
    fun isEmulator(): Boolean {
        return Build.BRAND.equals("android", ignoreCase = true) ||
            Build.MANUFACTURER.equals("android", ignoreCase = true) ||
            Build.HARDWARE.startsWith("goldfish") ||
            Build.HARDWARE.startsWith("ranchu") ||
            Build.HARDWARE.contains("vbox86") ||
            Build.MODEL.contains("sdk_gphone") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("AVD")
    }

    /**
     * Get a human-readable description of the current backend selection
     */
    fun getBackendDescription(): String {
        val backend = detectOptimalBackend()
        val gpuName = detectGpuName()

        return when (backend) {
            LlamaBackend.CPU -> "CPU only ($gpuName - GPU backend unavailable)"
            LlamaBackend.OPENCL -> "OpenCL ($gpuName - requires build fix)"
            LlamaBackend.VULKAN -> "Vulkan ($gpuName - GPU accelerated)"
        }
    }
}
