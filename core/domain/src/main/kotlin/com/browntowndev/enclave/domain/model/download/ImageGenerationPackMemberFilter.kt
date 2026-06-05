package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalStableDiffusionModelPolicy

/**
 * Identifies catalog IMAGE_GENERATION pack members for download gating when [ParsedModelCatalog] is unavailable.
 */
object ImageGenerationPackMemberFilter {
    fun sha256sFromCatalog(catalog: ParsedModelCatalog): Set<String> = catalog.packMemberSha256sForSlot(ModelType.IMAGE_GENERATION)

    fun sha256sFromAssets(assets: Iterable<LocalModelAsset>): Set<String> =
        assets.filter(::isLikelyPackMember).map { it.metadata.sha256 }.toSet()

    fun isLikelyPackMember(asset: LocalModelAsset): Boolean = LocalStableDiffusionModelPolicy.supports(asset)

    fun packMemberSha256sForApiOnboarding(result: DownloadModelsResult): Set<String> =
        result.imageGenerationPackMemberSha256s.ifEmpty {
            sha256sFromAssets(result.modelsToDownload + result.utilityAssets)
        }

    fun excludeFromDownloadResult(result: DownloadModelsResult): DownloadModelsResult {
        val packMemberShas = packMemberSha256sForApiOnboarding(result)
        if (packMemberShas.isEmpty()) return result
        return result.copy(
            modelsToDownload =
                result.modelsToDownload.filter { asset ->
                    asset.metadata.sha256 !in packMemberShas
                },
            utilityAssets =
                result.utilityAssets.filter { asset ->
                    asset.metadata.sha256 !in packMemberShas
                },
            availableToRedownload =
                result.availableToRedownload.filter { asset ->
                    asset.metadata.sha256 !in packMemberShas
                },
            imageGenerationPackMemberSha256s = packMemberShas,
        )
    }
}
