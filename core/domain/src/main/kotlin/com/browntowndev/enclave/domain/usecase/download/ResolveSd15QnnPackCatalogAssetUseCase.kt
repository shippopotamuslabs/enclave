package com.browntowndev.enclave.domain.usecase.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.download.Sd15QnnPackCatalog
import com.browntowndev.enclave.domain.model.download.Sd15QnnPackVariant
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.port.device.SnapdragonTierPort
import javax.inject.Inject

/**
 * Applies the device-appropriate `xororz/sd-qnn` zip to a catalog utility asset.
 */
class ResolveSd15QnnPackCatalogAssetUseCase
    @Inject
    constructor(
        private val snapdragonTierPort: SnapdragonTierPort,
    ) {
        operator fun invoke(asset: LocalModelAsset): LocalModelAsset {
            if (asset.metadata.utilityType != UtilityType.SD15_QNN_PACK) {
                return asset
            }
            return applyVariant(asset, Sd15QnnPackCatalog.variantFor(snapdragonTierPort.currentTier()))
        }

        fun applyVariant(
            asset: LocalModelAsset,
            variant: Sd15QnnPackVariant,
        ): LocalModelAsset {
            val meta = asset.metadata
            return asset.copy(
                metadata =
                    meta.copy(
                        huggingFaceModelName = Sd15QnnPackCatalog.HF_REPO,
                        huggingFacePath = null,
                        remoteFileName = variant.zipFileName,
                        localFileName = variant.localZipFileName,
                        sha256 = variant.sha256,
                        sizeInBytes = variant.sizeInBytes,
                        modelFileFormat = ModelFileFormat.BIN,
                        source = DownloadSource.HUGGING_FACE,
                        utilityType = UtilityType.SD15_QNN_PACK,
                        displayName = meta.displayName.ifBlank { "SD 1.5 NPU weights" },
                    ),
            )
        }

        fun syntheticAsset(): LocalModelAsset {
            val variant = Sd15QnnPackCatalog.variantFor(snapdragonTierPort.currentTier())
            val template = Sd15QnnPackCatalog.catalogTemplate
            val base =
                LocalModelAsset(
                    metadata =
                        LocalModelMetadata(
                            id = LocalModelId("sd15-qnn-pack"),
                            displayName = "SD 1.5 NPU weights",
                            huggingFaceModelName = Sd15QnnPackCatalog.HF_REPO,
                            remoteFileName = template.zipFileName,
                            localFileName = template.localZipFileName,
                            sha256 = template.sha256,
                            sizeInBytes = template.sizeInBytes,
                            modelFileFormat = ModelFileFormat.BIN,
                            source = DownloadSource.HUGGING_FACE,
                            utilityType = UtilityType.SD15_QNN_PACK,
                        ),
                    configurations = emptyList(),
                )
            return applyVariant(base, variant)
        }
    }
