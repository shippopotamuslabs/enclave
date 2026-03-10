package com.browntowndev.pocketcrew.domain.usecase.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.download.DownloadCheckResult
import com.browntowndev.pocketcrew.data.repository.DeviceEnvironmentRepository
import javax.inject.Inject

class ValidateDownloadConditionsUseCase @Inject constructor(
    private val deviceEnvironmentRepository: DeviceEnvironmentRepository
) {
    companion object {
        private const val TAG = "ValidateDownloadConditions"
    }

    operator fun invoke(
        missingModels: List<ModelConfiguration>,
        wifiOnly: Boolean
    ): DownloadCheckResult {
        if (missingModels.isEmpty()) {
            Log.d(TAG, "No missing models, can start")
            return DownloadCheckResult(true, null, missingModels)
        }

        val isWifi = deviceEnvironmentRepository.isWifiConnected()
        Log.d(TAG, "wifiOnly=$wifiOnly, isWifiConnected=$isWifi")

        if (wifiOnly && !isWifi) {
            Log.w(TAG, "WiFi-only mode enabled but not connected to WiFi")
            return DownloadCheckResult(
                false, "WiFi-only mode enabled but not connected to WiFi", missingModels
            )
        }

        if (!deviceEnvironmentRepository.hasRequiredStorage()) {
            Log.w(TAG, "Insufficient storage space")
            return DownloadCheckResult(
                false, "Insufficient storage space. Need at least 15 GB free.", missingModels
            )
        }

        Log.d(TAG, "All conditions met, can start")
        return DownloadCheckResult(true, null, missingModels)
    }
}
