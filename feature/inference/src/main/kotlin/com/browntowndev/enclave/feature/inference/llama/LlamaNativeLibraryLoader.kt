package com.browntowndev.enclave.feature.inference.llama

import android.os.Build
import android.util.Log

/**
 * Selects and loads the optimal llama-jni native library for the current device.
 */
internal object LlamaNativeLibraryLoader {
    private const val TAG = "LlamaNativeLibraryLoader"

    private const val LIBRARY_CPUDETECT = "cpudetect"
    private const val LIBRARY_SVE = "llama-jni-sve"
    private const val LIBRARY_NEON = "llama-jni-neon"

    /**
     * Load the optimal native library based on hardware SVE support.
     *
     * @param detectSveBits JNI hook registered on [JniLlamaEngine] (cpudetect.so symbol path).
     */
    fun loadOptimalLibrary(detectSveBits: () -> Int) {
        try {
            Log.i(TAG, "Loading CPU detector: $LIBRARY_CPUDETECT")
            System.loadLibrary(LIBRARY_CPUDETECT)
        } catch (e: Throwable) {
            Log.e(TAG, "Critical: cpudetect library failed to load. Are we in a unit test?", e)
            return
        }

        val sveBits =
            try {
                detectSveBits()
            } catch (e: Throwable) {
                Log.w(TAG, "SVE detection failed: ${e.message}")
                0
            }

        val isBlacklisted = isKnownSveBlacklisted()
        val libraryToLoad =
            when {
                isBlacklisted.first -> {
                    Log.w(TAG, "Blacklisted device: ${isBlacklisted.second}. Using NEON.")
                    LIBRARY_NEON
                }
                sveBits >= 256 -> {
                    Log.i(TAG, "SVE $sveBits-bit confirmed. Loading SVE library.")
                    LIBRARY_SVE
                }
                sveBits > 0 -> {
                    Log.i(TAG, "SVE present but narrow ($sveBits-bit < 256). Using NEON.")
                    LIBRARY_NEON
                }
                else -> {
                    Log.i(TAG, "No SVE support detected. Using NEON.")
                    LIBRARY_NEON
                }
            }

        try {
            Log.i(TAG, "Loading llama library: $libraryToLoad")
            System.loadLibrary(libraryToLoad)
            Log.i(TAG, "Library loaded successfully")
        } catch (e: Throwable) {
            Log.e(TAG, "Critical: Failed to load $libraryToLoad", e)
        }
    }

    /** Devices with broken 128-bit SVE should use the NEON build. */
    fun isKnownSveBlacklisted(): Pair<Boolean, String> {
        val socModel = Build.SOC_MODEL.uppercase()
        if (socModel == "GS301") {
            return Pair(true, "Tensor G3 (GS301)")
        }

        val device = Build.DEVICE.lowercase()
        val pixel8Family = setOf("shiba", "husky", "akita")
        if (device in pixel8Family) {
            return Pair(true, "Pixel 8 family ($device)")
        }

        return Pair(false, "")
    }
}
