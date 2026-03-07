package com.browntowndev.pocketcrew.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import com.browntowndev.pocketcrew.domain.model.ModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for device environment checks.
 *
 * Provides access to system-level information including network connectivity
 * and storage availability. Extracted from ModelDownloadManager to enable
 * better testability and separation of concerns.
 *
 * @param context The application context
 */
@Singleton
class DeviceEnvironmentRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DeviceEnvironmentRepo"
    }

    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    /**
     * Checks if the device is currently connected to WiFi.
     *
     * @return true if WiFi connection is available, false otherwise
     */
    fun isWifiConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Checks if any network connection is available.
     *
     * @return true if network is available (WiFi, cellular, or ethernet), false otherwise
     */
    fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Checks if the device has sufficient storage space.
     *
     * Uses the app's external files directory for storage calculation.
     * Returns true if unable to determine storage (optimistic behavior).
     *
     * @param requiredBytes The minimum required free space in bytes
     * @return true if sufficient space is available, false otherwise
     */
    fun hasSufficientSpace(requiredBytes: Long): Boolean {
        return try {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val stat = StatFs(baseDir.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            val hasSpace = availableBytes >= requiredBytes

            if (!hasSpace) {
                Log.w(
                    TAG,
                    "Insufficient storage: ${availableBytes / (1024 * 1024 * 1024)} GB available, " +
                        "need ${requiredBytes / (1024 * 1024 * 1024)} GB"
                )
            }

            hasSpace
        } catch (e: Exception) {
            Log.e(TAG, "Error checking storage: ${e.message}")
            true
        }
    }

    /**
     * Checks if the device has the default required storage (15 GB).
     *
     * @return true if at least 15 GB is available, false otherwise
     */
    fun hasRequiredStorage(): Boolean {
        return hasSufficientSpace(ModelConfig.REQUIRED_FREE_SPACE_BYTES)
    }
}
