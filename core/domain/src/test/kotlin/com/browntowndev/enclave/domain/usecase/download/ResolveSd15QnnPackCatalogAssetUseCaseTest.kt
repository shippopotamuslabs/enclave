package com.browntowndev.enclave.domain.usecase.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.device.SnapdragonTier
import com.browntowndev.enclave.domain.model.download.Sd15QnnPackCatalog
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.port.device.SnapdragonTierPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResolveSd15QnnPackCatalogAssetUseCaseTest {
    private val useCase =
        ResolveSd15QnnPackCatalogAssetUseCase(
            object : SnapdragonTierPort {
                override fun currentTier(): SnapdragonTier = SnapdragonTier.UNKNOWN
            },
        )

    @Test
    fun invoke_unknownTierSelectsMinZip() {
        val asset = baseSd15Asset()
        val resolved = useCase.invoke(asset)
        val min = Sd15QnnPackCatalog.variantFor(SnapdragonTier.UNKNOWN)
        assertEquals(min.zipFileName, resolved.metadata.localFileName)
        assertEquals(min.sha256, resolved.metadata.sha256)
    }

    private fun baseSd15Asset(): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("sd15-qnn-pack"),
                    huggingFaceModelName = Sd15QnnPackCatalog.HF_REPO,
                    remoteFileName = "template.zip",
                    localFileName = "template.zip",
                    sha256 = "abc",
                    sizeInBytes = 1L,
                    modelFileFormat = ModelFileFormat.BIN,
                    utilityType = UtilityType.SD15_QNN_PACK,
                ),
            configurations = emptyList(),
        )
}