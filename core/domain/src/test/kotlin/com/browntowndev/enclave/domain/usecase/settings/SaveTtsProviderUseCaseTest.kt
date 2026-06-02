package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.port.repository.TtsProviderRepositoryPort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaveTtsProviderUseCaseTest {
    private val repository = mockk<TtsProviderRepositoryPort>()

    private data class Captured(
        val asset: TtsProviderAsset,
        val apiKey: String?,
    )

    private fun stub(): () -> Captured {
        val box = arrayOf<Captured?>(null)
        coEvery { repository.saveTtsProvider(any(), any()) } coAnswers {
            box[0] = Captured(firstArg(), secondArg())
            TtsProviderId("saved-id")
        }
        return { requireNotNull(box[0]) { "saveTtsProvider was not called" } }
    }

    @Test
    fun `reuses sourceCredentialAlias when set`() =
        runTest {
            val capturedFn = stub()

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

            val captured = capturedFn()
            assertEquals("openai", captured.asset.credentialAlias)
            assertNull(captured.apiKey)
        }

    @Test
    fun `falls back to draft credentialAlias when sourceCredentialAlias is null`() =
        runTest {
            val capturedFn = stub()

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

            val captured = capturedFn()
            assertEquals("google-2", captured.asset.credentialAlias)
            assertEquals("sk-test", captured.apiKey)
        }

    @Test
    fun `propagates modelName to saved asset`() =
        runTest {
            val capturedFn = stub()

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

            assertEquals("gemini-3.1-flash-tts-preview", capturedFn().asset.modelName)
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
                            apiKey = "key",
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
            val capturedFn = stub()

            SaveTtsProviderUseCase(repository)
                .invoke(
                    TtsProviderDraft(
                        displayName = "FAL TTS",
                        provider = ApiProvider.FAL,
                        voiceName = "Wise_Woman",
                        modelName = "fal-ai/minimax/speech-02-hd",
                        credentialAlias = "fal",
                        apiKey = "key",
                    ),
                ).getOrThrow()

            assertEquals("fal-ai/minimax/speech-02-hd", capturedFn().asset.modelName)
        }
}
