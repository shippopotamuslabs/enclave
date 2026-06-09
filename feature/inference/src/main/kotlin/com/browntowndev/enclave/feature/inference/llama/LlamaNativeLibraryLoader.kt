package com.browntowndev.enclave.feature.inference.llama

import android.os.Build
import android.util.Log

/**
 * Selects and loads the optimal llama-jni native library for the current device.
 */
internal object LlamaNativeLibraryLoader {
    private const val TAG = "LlamaNativeLibraryLoader"
    private const val MIN_SVE_BITS = 256

    const val LIBRARY_CPUDETECT = "cpudetect"
    const val LIBRARY_SVE = "llama-jni-sve"
    const val LIBRARY_NEON = "llama-jni-neon"

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

        val blacklist = isKnownSveBlacklisted()
        val preferred = resolvePreferredLibrary(sveBits = sveBits, isBlacklisted = blacklist.first)
        if (blacklist.first) {
            Log.w(TAG, "Blacklisted device: ${blacklist.second}. Using NEON.")
        } else if (preferred == LIBRARY_SVE) {
            Log.i(TAG, "SVE $sveBits-bit confirmed. Loading SVE library.")
        } else if (sveBits > 0) {
            Log.i(TAG, "SVE present but narrow ($sveBits-bit < $MIN_SVE_BITS). Using NEON.")
        } else {
            Log.i(TAG, "No SVE support detected. Using NEON.")
        }

        val loaded =
            loadLibraryWithFallback(
                preferred = preferred,
                fallback = LIBRARY_NEON,
            )
        if (loaded == null) {
            Log.e(TAG, "Critical: Failed to load llama native library (preferred=$preferred)")
        } else {
            Log.i(TAG, "Library loaded successfully: $loaded")
        }
    }

    /**
     * Picks the llama-jni variant for this device. SVE is only selected when hardware reports
     * at least [MIN_SVE_BITS] and the device is not on the known-bad list.
     */
    fun resolvePreferredLibrary(
        sveBits: Int,
        isBlacklisted: Boolean,
    ): String =
        when {
            isBlacklisted -> LIBRARY_NEON
            sveBits >= MIN_SVE_BITS -> LIBRARY_SVE
            else -> LIBRARY_NEON
        }

    /**
     * Attempts [preferred] first, then [fallback] when the preferred build is missing from the APK.
     */
    fun loadLibraryWithFallback(
        preferred: String,
        fallback: String,
    ): String? {
        if (preferred == fallback) {
            return runCatching {
                Log.i(TAG, "Loading llama library: $preferred")
                System.loadLibrary(preferred)
                preferred
            }.onFailure { error ->
                Log.e(TAG, "Critical: Failed to load $preferred", error)
            }.getOrNull()
        }

        runCatching {
            Log.i(TAG, "Loading llama library: $preferred")
            System.loadLibrary(preferred)
            return preferred
        }.onFailure { error ->
            Log.w(TAG, "Preferred library $preferred unavailable, falling back to $fallback", error)
        }

        return runCatching {
            Log.i(TAG, "Loading llama library: $fallback")
            System.loadLibrary(fallback)
            fallback
        }.onFailure { error ->
            Log.e(TAG, "Critical: Failed to load fallback $fallback", error)
        }.getOrNull()
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
