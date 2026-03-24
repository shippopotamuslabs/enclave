package com.browntowndev.pocketcrew.feature.inference.llama

import android.os.Build
import android.util.Log
import java.io.File

/**
 * SVE (Scalable Vector Extensions) runtime detector for optimal native library selection.
 *
 * This class determines whether to load the SVE-optimized or NEON-optimized native library
 * based on hardware capability detection.
 *
 * Detection Priority:
 * 1. Vector Length: Read /proc/sys/abi/sve_default_vector_length (primary method)
 * 2. CPU Features: Fallback to /proc/cpuinfo parsing
 * 3. Device Blacklist: Explicit blocklist for known problematic devices (Tensor G3)
 *
 * Key insight: SVE is only beneficial if vector width > 128-bit (32 bytes).
 * Devices with 128-bit SVE (like Tensor G3) should use NEON for stability.
 */
object SveDetector {

    private const val TAG = "SveDetector"

    // SVE minimum beneficial vector length: 32 bytes = 256-bit
    // 128-bit SVE (16 bytes) offers no benefit over NEON and has historical instability
    private const val SVE_MIN_VECTOR_BYTES = 32

    /**
     * Determine if the device should use SVE-optimized native library.
     *
     * @return true if SVE library should be used, false for NEON library
     */
    fun shouldUseSve(): Boolean {
        // 1. Check Vector Length (Primary Method)
        val sveVectorBytes = readSveVectorLength()

        if (sveVectorBytes != null) {
            Log.d(TAG, "Kernel reports SVE vector length: $sveVectorBytes bytes")
            return sveVectorBytes >= SVE_MIN_VECTOR_BYTES
        }

        // 2. Fallback: Check /proc/cpuinfo for SVE feature
        if (!cpuHasSveFeature()) {
            Log.d(TAG, "CPU does not support SVE, using NEON")
            return false
        }

        // 3. Blacklist known problematic devices (Tensor G3)
        if (isKnownNarrowSveDevice()) {
            Log.w(TAG, "Device on SVE blacklist (known 128-bit SVE issue), forcing NEON")
            return false
        }

        // 4. Final decision: If SVE present and not blacklisted, try SVE
        // This is risky if we couldn't read vector length, but better than nothing
        Log.w(TAG, "Could not determine vector length, attempting SVE")
        return true
    }

    /**
     * Read SVE vector length from kernel sysfs.
     * Returns null if unable to read (SELinux / permissions / not SVE hardware).
     */
    private fun readSveVectorLength(): Int? {
        return try {
            val file = File("/proc/sys/abi/sve_default_vector_length")
            if (file.exists() && file.canRead()) {
                file.readText().trim().toIntOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not read SVE vector length: ${e.message}")
            null
        }
    }

    /**
     * Check if CPU supports SVE by parsing /proc/cpuinfo.
     */
    private fun cpuHasSveFeature(): Boolean {
        return try {
            val cpuInfo = File("/proc/cpuinfo").readText().lowercase()
            // Regex ensures we match 'sve' or 'sve2' as whole words (not part of another word)
            Regex("\\bsve2?\\b").containsMatchIn(cpuInfo)
        } catch (e: Exception) {
            Log.d(TAG, "Could not read cpuinfo: ${e.message}")
            false
        }
    }

    /**
     * Check if device is known to have problematic (128-bit) SVE implementation.
     * These devices should use NEON for stability.
     */
    private fun isKnownNarrowSveDevice(): Boolean {
        // Tensor G3 (Pixel 8 family) - GS301 SoC
        // Known issue: SVE crashes due to incorrect vector length handling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (Build.SOC_MODEL.equals("GS301", ignoreCase = true)) {
                Log.d(TAG, "Detected Tensor G3 (GS301) - known 128-bit SVE issue")
                return true
            }
        }

        // Fallback to device codenames for Pixel 8 family
        val device = Build.DEVICE.lowercase()
        // Pixel 8 Pro: shiba, husky; Pixel 8: akita
        val pixel8Family = setOf("shiba", "husky", "akita")
        if (device in pixel8Family) {
            Log.d(TAG, "Detected Pixel 8 family device: $device")
            return true
        }

        return false
    }

    /**
     * Get descriptive string for logging which library will be loaded.
     */
    fun getLibrarySelectionReason(): String {
        val sveVectorBytes = readSveVectorLength()

        if (sveVectorBytes != null) {
            return if (sveVectorBytes >= SVE_MIN_VECTOR_BYTES) {
                "SVE vector length $sveVectorBytes bytes >= ${SVE_MIN_VECTOR_BYTES} bytes - using SVE"
            } else {
                "SVE vector length $sveVectorBytes bytes < ${SVE_MIN_VECTOR_BYTES} bytes - using NEON"
            }
        }

        if (!cpuHasSveFeature()) {
            return "No SVE support in CPU - using NEON"
        }

        if (isKnownNarrowSveDevice()) {
            return "Device blacklisted (Tensor G3) - using NEON"
        }

        return "SVE supported but vector length unknown - attempting SVE"
    }
}
