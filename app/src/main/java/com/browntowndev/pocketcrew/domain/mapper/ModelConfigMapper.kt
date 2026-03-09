package com.browntowndev.pocketcrew.domain.mapper

import com.browntowndev.pocketcrew.domain.model.inference.ModelFile
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import javax.inject.Inject
import javax.inject.Singleton

private const val MODEL_BASE_URL = "https://pub-83e071f6f35749ed990eeb3058fc863d.r2.dev"

/**
 * Mapper to convert between model configuration types and ModelFile.
 * Handles conversion from RegisteredModel and RemoteModelConfig to domain ModelFile.
 */
@Singleton
class ModelConfigMapper @Inject constructor() {
    /**
         * Converts a collection of ModelConfiguration to a list of ModelFile.
         * Groups by SHA256 to collect all modelTypes shared by the same remote file.
         */
    fun toModelFiles(registeredModels: Collection<ModelConfiguration>): List<ModelFile> {
        return registeredModels
            .groupBy { it.metadata.sha256 }
            .map { (sha256, models) ->
                val first = models.first()
                ModelFile(
                    sizeBytes = first.metadata.sizeInBytes,
                    url = "$MODEL_BASE_URL/${first.metadata.remoteFileName}",
                    sha256 = sha256,
                    modelTypes = models.map { it.modelType }.distinct(),
                    originalFileName = first.metadata.remoteFileName,
                    displayName = first.metadata.displayName,
                    modelFileFormat = first.metadata.modelFileFormat,
                    temperature = first.tunings.temperature,
                    topK = first.tunings.topK,
                    topP = first.tunings.topP,
                    maxTokens = first.tunings.maxTokens,
                    systemPrompt = first.persona.systemPrompt
                )
            }
    }
}
