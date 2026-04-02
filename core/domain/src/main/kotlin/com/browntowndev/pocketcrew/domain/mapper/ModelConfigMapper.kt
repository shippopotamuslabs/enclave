package com.browntowndev.pocketcrew.domain.mapper

import com.browntowndev.pocketcrew.domain.model.inference.ModelFile
import com.browntowndev.pocketcrew.domain.model.config.RemoteModelConfig
import javax.inject.Inject
import javax.inject.Singleton

private const val MODEL_BASE_URL = "https://config.pocketcrew.app"

/**
 * Mapper to convert between model configuration types and ModelFile.
 * Handles conversion from RegisteredModel and RemoteModelConfig to domain ModelFile.
 */
@Singleton
class ModelConfigMapper @Inject constructor() {
    /**
         * Converts a collection of RemoteModelConfig to a list of ModelFile.
         * Groups by SHA256 to collect all modelTypes shared by the same remote file.
         */
    fun toModelFiles(registeredModels: Collection<RemoteModelConfig>): List<ModelFile> {
        return registeredModels
            .groupBy { it.sha256 }
            .map { (sha256, models) ->
                val first = models.first()
                ModelFile(
                    sizeBytes = first.sizeInBytes,
                    url = "$MODEL_BASE_URL/${first.fileName}",
                    sha256 = sha256,
                    modelTypes = models.map { it.modelType }.distinct(),
                    originalFileName = first.fileName,
                    displayName = first.displayName,
                    modelFileFormat = first.modelFileFormat,
                    temperature = first.temperature,
                    topK = first.topK,
                    topP = first.topP,
                    maxTokens = first.maxTokens,
                    systemPrompt = first.systemPrompt,
                    visionCapable = first.visionCapable
                )
            }
    }
}
