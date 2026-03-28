package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.DraftOneModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.DraftTwoModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.FastModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.FinalSynthesizerModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.MainModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ThinkingModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.VisionModelEngine
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import javax.inject.Inject

/**
 * Stub InferenceFactoryPort — always returns on-device engine.
 * Future ticket adds API branch with DefaultModelRepositoryPort check.
 */
class InferenceFactoryImpl @Inject constructor(
    @FastModelEngine private val fastOnDevice: LlmInferencePort,
    @ThinkingModelEngine private val thinkingOnDevice: LlmInferencePort,
    @MainModelEngine private val mainOnDevice: LlmInferencePort,
    @DraftOneModelEngine private val draftOneOnDevice: LlmInferencePort,
    @DraftTwoModelEngine private val draftTwoOnDevice: LlmInferencePort,
    @FinalSynthesizerModelEngine private val finalSynthOnDevice: LlmInferencePort,
    @VisionModelEngine private val visionOnDevice: LlmInferencePort,
    private val loggingPort: LoggingPort,
) : InferenceFactoryPort {

    companion object {
        private const val TAG = "InferenceFactory"
    }

    override suspend fun getInferenceService(modelType: ModelType): LlmInferencePort {
        // TODO(ticket:BYOK-1): Implement API routing checking DefaultModelRepositoryPort
        loggingPort.debug(TAG, "Resolving $modelType → ON_DEVICE (stub)")
        return when (modelType) {
            ModelType.FAST -> fastOnDevice
            ModelType.THINKING -> thinkingOnDevice
            ModelType.MAIN -> mainOnDevice
            ModelType.DRAFT_ONE -> draftOneOnDevice
            ModelType.DRAFT_TWO -> draftTwoOnDevice
            ModelType.FINAL_SYNTHESIS -> finalSynthOnDevice
            ModelType.VISION -> visionOnDevice
        }
    }
}
