package com.browntowndev.pocketcrew.domain.mapper

import com.browntowndev.pocketcrew.domain.model.inference.ModelFile
import com.browntowndev.pocketcrew.domain.model.config.RemoteModelAsset
import javax.inject.Inject
import javax.inject.Singleton

private const val MODEL_BASE_URL = "https://config.pocketcrew.app"

/**
 * Mapper to convert between model configuration types and ModelFile.
 * Handles conversion from RemoteModelAsset to domain ModelFile.
 */
@Singleton
class ModelConfigMapper @Inject constructor() {
    /**
     * Converts a collection of RemoteModelAsset to a list of ModelFile.
     * Each asset maps to one ModelFile; configurations within an asset share the same file.
     * The modelTypes are derived from the union of all defaultAssignments across configurations.
     */
    fun toModelFiles(assets: Collection<RemoteModelAsset>): List<ModelFile> {
        return assets.map { asset ->
            val allModelTypes = asset.configurations
                .flatMap { it.defaultAssignments }
                .distinct()

            val primaryConfig = asset.configurations.firstOrNull()
            ModelFile(
                sizeBytes = asset.sizeInBytes,
                url = "$MODEL_BASE_URL/${asset.fileName}",
                sha256 = asset.sha256,
                modelTypes = allModelTypes,
                originalFileName = asset.fileName,
                displayName = primaryConfig?.displayName ?: asset.fileName,
                modelFileFormat = asset.modelFileFormat,
                temperature = primaryConfig?.temperature ?: 0.7,
                topK = primaryConfig?.topK ?: 40,
                topP = primaryConfig?.topP ?: 0.95,
                maxTokens = primaryConfig?.maxTokens ?: 4096,
                systemPrompt = primaryConfig?.systemPrompt ?: "",
                isMultimodal = asset.isMultimodal
            )
        }
    }
}
