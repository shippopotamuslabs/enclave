package com.browntowndev.enclave.domain.usecase.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.download.DownloadCheckResult
import com.browntowndev.enclave.domain.port.repository.DeviceEnvironmentRepositoryPort
import javax.inject.Inject

class ValidateDownloadConditionsUseCase
    @Inject
    constructor(
        private val deviceEnvironmentRepository: DeviceEnvironmentRepositoryPort,
    ) {
        suspend operator fun invoke(
            missingModels: List<LocalModelAsset>,
            wifiOnly: Boolean,
        ): DownloadCheckResult {
            if (missingModels.isEmpty()) {
                return DownloadCheckResult(true, null, missingModels)
            }

            val isWifi = deviceEnvironmentRepository.isWifiConnected()

            if (wifiOnly && !isWifi) {
                return DownloadCheckResult(
                    false,
                    "WiFi-only mode enabled but not connected to WiFi",
                    missingModels,
                )
            }

            if (!deviceEnvironmentRepository.hasRequiredStorage(15L * 1024 * 1024 * 1024)) {
                return DownloadCheckResult(
                    false,
                    "Insufficient storage space. Need at least 15 GB free.",
                    missingModels,
                )
            }

            return DownloadCheckResult(true, null, missingModels)
        }
    }
