package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.core.testing.FakeTtsProviderRepository
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaveTtsProviderUseCaseTest {
    private val repository = FakeTtsProviderRepository()

    @Test
    fun `reuses sourceCredentialAlias when set`() =
        runTest {
            SaveTtsProviderUseCase(repository)
                .invoke(
                    TtsProviderDraft(
                        displayName = "My TTS",
                        provider = ApiProvider.OPENAI,
                        voiceName = "alloy",
                        credentialAlias = "",
                        apiKey = "",
                        sourceCredentialAlias = "openai",
                    ),
                ).getOrThrow()

            val captured = repository.savedCalls.single()
            assertEquals("openai", captured.first.credentialAlias)
            assertNull(captured.second)
        }

    @Test
    fun `falls back to draft credentialAlias when sourceCredentialAlias is null`() =
        runTest {
            SaveTtsProviderUseCase(repository)
                .invoke(
                    TtsProviderDraft(
                        displayName = "My TTS",
                        provider = ApiProvider.OPENAI,
                        voiceName = "alloy",
                        credentialAlias = "google-2",
                        apiKey = "sk-test",
                        sourceCredentialAlias = null,
                    ),
                ).getOrThrow()

            val captured = repository.savedCalls.single()
            assertEquals("google-2", captured.first.credentialAlias)
            assertEquals("sk-test", captured.second)
        }

    @Test
    fun `propagates modelName to saved asset`() =
        runTest {
            SaveTtsProviderUseCase(repository)
                .invoke(
                    TtsProviderDraft(
                        displayName = "My TTS",
                        provider = ApiProvider.OPENAI,
                        voiceName = "alloy",
                        modelName = "gemini-3.1-flash-tts-preview",
                        credentialAlias = "google-2",
                        apiKey = "sk-test",
                    ),
                ).getOrThrow()

            assertEquals("gemini-3.1-flash-tts-preview", repository.savedCalls.single().first.modelName)
        }

    @Test
    fun `rejects unsupported fal tts model id`() =
        runTest {
            val result =
                SaveTtsProviderUseCase(repository)
                    .invoke(
                        TtsProviderDraft(
                            displayName = "FAL TTS",
                            provider = ApiProvider.FAL,
                            voiceName = "Wise_Woman",
                            modelName = "fal-ai/elevenlabs/tts/turbo-v2.5",
                            credentialAlias = "fal",
                            apiKey = "fal-key",
                        ),
                    )

            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull()?.message?.contains("not a supported fal.ai TTS endpoint") == true,
            )
        }

    @Test
    fun `accepts supported fal tts model id`() =
        runTest {
            SaveTtsProviderUseCase(repository)
                .invoke(
                    TtsProviderDraft(
                        displayName = "FAL TTS",
                        provider = ApiProvider.FAL,
                        voiceName = "Wise_Woman",
                        modelName = "fal-ai/minimax/speech-02-hd",
                        credentialAlias = "fal",
                        apiKey = "fal-key",
                    ),
                ).getOrThrow()

            assertEquals("fal-ai/minimax/speech-02-hd", repository.savedCalls.single().first.modelName)
        }
}
