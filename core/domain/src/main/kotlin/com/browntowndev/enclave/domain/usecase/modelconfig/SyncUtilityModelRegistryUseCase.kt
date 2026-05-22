package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import javax.inject.Inject

class SyncUtilityModelRegistryUseCase
    @Inject
    constructor(
        private val utilityModelRepository: UtilityModelRepositoryPort,
        private val logger: LoggingPort,
    ) {
        suspend operator fun invoke(asset: LocalModelAsset): UtilityModelId {
            val utilityType =
                asset.metadata.utilityType
                    ?: error("Not a utility asset")
            val metadata =
                UtilityModelMetadata(
                    id = UtilityModelId(""),
                    utilityType = utilityType,
                    huggingFaceModelName = asset.metadata.huggingFaceModelName,
                    remoteFileName = asset.metadata.remoteFileName,
                    localFileName = asset.metadata.localFileName,
                    sha256 = asset.metadata.sha256,
                    sizeInBytes = asset.metadata.sizeInBytes,
                    modelFileFormat = asset.metadata.modelFileFormat,
                    source = asset.metadata.source,
                )
            val id = utilityModelRepository.upsert(metadata)
            logger.debug(
                TAG,
                "SyncUtilityModelRegistry($utilityType): sha=${metadata.sha256} file=${metadata.localFileName}",
            )
            return id
        }

        private companion object {
            private const val TAG = "SyncUtilityModelRegistry"
        }
    }
