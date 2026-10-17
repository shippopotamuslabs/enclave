package com.browntowndev.enclave.feature.studio

import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeApiModelCatalogPort
import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeAudioCapture
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeDistributionConfiguration
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeMusicGenerationPort
import com.browntowndev.enclave.core.testing.FakeTrustedDomainRepository
import com.browntowndev.enclave.core.testing.FakeUtilityModelFile
import com.browntowndev.enclave.core.testing.FakeWhisperInference
import com.browntowndev.enclave.core.testing.TestClock
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ProviderCapabilities
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.model.media.StudioModelOverride
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.CreateAssistantPlaceholderUseCase
import com.browntowndev.enclave.domain.usecase.chat.GenerateChatResponseUseCase
import com.browntowndev.enclave.domain.usecase.chat.GenerateChatTitleUseCase
import com.browntowndev.enclave.domain.usecase.chat.GetChatCapablePresetsUseCase
import com.browntowndev.enclave.domain.usecase.chat.GetChatUseCase
import com.browntowndev.enclave.domain.usecase.chat.ListenToSpeechUseCase
import com.browntowndev.enclave.domain.usecase.chat.MergeDbMessagesWithOptimisticUseCase
import com.browntowndev.enclave.domain.usecase.chat.MergeMessagesUseCase
import com.browntowndev.enclave.domain.usecase.chat.ProcessFileAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.chat.SubmitPromptUseCase
import com.browntowndev.enclave.domain.usecase.inference.ShouldModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateMusicUseCase
import com.browntowndev.enclave.domain.usecase.media.GetProviderCapabilitiesUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveMediaProviderCapabilitiesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Recording use-case fakes for the studio suites (ENC-28). Each subclasses the
 * real use case (opened for testability) with dummy collaborators and overrides
 * invoke with test-controlled behavior.
 */

class FakeShouldModeratePromptUseCase(
    var shouldModerate: Boolean = true,
) : ShouldModeratePromptUseCase(
        FakeDistributionConfiguration(),
        FakeActiveModelProvider(),
        FakeTrustedDomainRepository(),
        FakeLoggingPort(),
    ) {
    override suspend fun invoke(modelType: ModelType): Boolean = shouldModerate
}

class FakeResolveMediaProviderCapabilitiesUseCase(
    var result: ProviderCapabilities,
) : ResolveMediaProviderCapabilitiesUseCase(
        GetProviderCapabilitiesUseCase(),
        FakeApiModelCatalogPort(),
        FakeApiKeyProvider(),
    ) {
    val calls = mutableListOf<Pair<MediaProviderAsset?, MediaCapability>>()

    /** When set, invoke suspends until the gate completes (loading-state tests). */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun invoke(
        mediaProvider: MediaProviderAsset?,
        mediaCapability: MediaCapability,
    ): ProviderCapabilities {
        calls += mediaProvider to mediaCapability
        gate?.await()
        return result
    }
}

class FakeListenToSpeechUseCase : ListenToSpeechUseCase(
    FakeAudioCapture(),
    FakeWhisperInference(),
    FakeUtilityModelFile(),
    TestClock(),
) {
    val events = MutableSharedFlow<SpeechState>(extraBufferCapacity = 8)
    var callCount = 0

    override operator fun invoke(initialText: String, stopSignal: StateFlow<Boolean>): Flow<SpeechState> {
        callCount++
        return events
    }
}

/** ChatUseCases fake: only listenToSpeechUseCase is wired; the rest throw if touched. */
class FakeChatUseCases(
    override val listenToSpeechUseCase: ListenToSpeechUseCase,
) : ChatUseCases {
    private fun unused(): Nothing =
        throw UnsupportedOperationException("Not used by studio tests")

    override val createAssistantPlaceholderUseCase: CreateAssistantPlaceholderUseCase
        get() = unused()
    override val submitPromptUseCase: SubmitPromptUseCase
        get() = unused()
    override val mergeDbMessagesWithOptimisticUseCase: MergeDbMessagesWithOptimisticUseCase
        get() = unused()
    override val generateChatResponseUseCase: GenerateChatResponseUseCase
        get() = unused()
    override val getChatCapablePresetsUseCase: GetChatCapablePresetsUseCase
        get() = unused()
    override val generateChatTitleUseCase: GenerateChatTitleUseCase
        get() = unused()
    override val getChatUseCase: GetChatUseCase
        get() = unused()
    override val mergeMessagesUseCase: MergeMessagesUseCase
        get() = unused()
    override val processFileAttachmentUseCase: ProcessFileAttachmentUseCase
        get() = unused()
}

class FakeGenerateMusicUseCase :
    GenerateMusicUseCase(FakeDefaultModelRepository(), FakeMediaProviderRepository(), FakeMusicGenerationPort()) {
    var result: Result<ByteArray> = Result.success(ByteArray(0))
    val calls = mutableListOf<Pair<String, GenerationSettings>>()

    override suspend fun invoke(
        prompt: String,
        settings: GenerationSettings,
        override: StudioModelOverride?,
    ): Result<ByteArray> {
        calls += prompt to settings
        return result
    }
}
