package com.browntowndev.pocketcrew.feature.inference.compaction

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.CompactionProviderType
import com.browntowndev.pocketcrew.domain.port.inference.CompactionPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompactionOrchestrator @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val inferenceFactory: InferenceFactoryPort,
    private val loggingPort: LoggingPort,
) : CompactionPort {

    override suspend fun compactHistory(history: List<ChatMessage>): String? {
        val settings = settingsRepository.settingsFlow.first()
        val providerType = settings.compactionProviderType

        if (providerType == CompactionProviderType.DISABLED) return null

        val provider = when (providerType) {
            CompactionProviderType.DISABLED -> return null
            CompactionProviderType.API_MODEL -> {
                val modelId = settings.compactionApiModelId ?: return null
                ApiModelCompactor(modelId, inferenceFactory, loggingPort)
            }
            CompactionProviderType.TINY_ONNX -> null // Not implemented yet
        } ?: return null

        return try {
            provider.compact(history)
        } catch (e: Exception) {
            loggingPort.error("CompactionOrchestrator", "Compaction failed for provider ${provider.name}: ${e.message}")
            null
        }
    }
}
