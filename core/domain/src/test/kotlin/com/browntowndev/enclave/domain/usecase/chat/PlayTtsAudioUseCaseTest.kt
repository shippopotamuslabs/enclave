package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeAudioPlayer
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeLiveVoiceProviderRepository
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeTtsProviderRepository
import com.browntowndev.enclave.core.testing.FakeTtsService
import com.browntowndev.enclave.core.testing.FakeTtsServiceFactory
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCaseImpl
import com.browntowndev.enclave.domain.usecase.settings.GetLiveVoiceProvidersUseCase
import com.browntowndev.enclave.domain.usecase.settings.GetTtsProvidersUseCase
import com.browntowndev.enclave.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayTtsAudioUseCaseTest {
    @Test
    fun `invoke synthesizes and plays audio on success`() =
        runTest {
            val ttsAsset = TtsProviderAsset(TtsProviderId("1"), "OpenAI", ApiProvider.OPENAI, "alloy", null, null, "alias-1")
            val defaultModelRepository = FakeDefaultModelRepository()
            val ttsProviderRepository = FakeTtsProviderRepository()
            defaultModelRepository.seed(
                DefaultModelAssignment(modelType = ModelType.TTS, ttsProviderId = ttsAsset.id),
            )
            ttsProviderRepository.emit(listOf(ttsAsset))
            val resolveSelectionUseCase =
                ResolveAssignedModelSelectionUseCase(
                    GetDefaultModelsUseCaseImpl(defaultModelRepository),
                    GetLocalModelAssetsUseCaseImpl(FakeLocalModelRepository()),
                    GetApiModelAssetsUseCaseImpl(FakeApiModelRepository()),
                    GetTtsProvidersUseCase(ttsProviderRepository),
                    GetLiveVoiceProvidersUseCase(FakeLiveVoiceProviderRepository()),
                )
            val audioBytes = byteArrayOf(1, 2, 3)
            val ttsService = FakeTtsService()
            ttsService.synthesizeResult = Result.success(audioBytes)
            val ttsFactory = FakeTtsServiceFactory()
            ttsFactory.batchService = ttsService
            val audioPlayer = FakeAudioPlayer()
            val apiKeyProvider = FakeApiKeyProvider()
            apiKeyProvider.saveApiKey("alias-1", "sk-test")
            val useCase = PlayTtsAudioUseCase(resolveSelectionUseCase, ttsFactory, audioPlayer, apiKeyProvider)

            val result = useCase("Hello")

            assertTrue(result.isSuccess)
            assertEquals(
                listOf(FakeTtsServiceFactory.CreateCall(ApiProvider.OPENAI, "sk-test", null)),
                ttsFactory.createCalls,
            )
            assertEquals(
                listOf(FakeTtsService.SynthesizeCall("Hello", "alloy", null)),
                ttsService.synthesizeCalls,
            )
            assertEquals(1, audioPlayer.playAudioCalls.size)
            assertTrue(audioPlayer.playAudioCalls.single().contentEquals(audioBytes))
        }
}
