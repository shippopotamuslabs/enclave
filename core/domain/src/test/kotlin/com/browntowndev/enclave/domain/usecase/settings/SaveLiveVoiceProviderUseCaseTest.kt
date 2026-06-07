package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.port.repository.LiveVoiceProviderRepositoryPort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SaveLiveVoiceProviderUseCaseTest {
    private val repository = mockk<LiveVoiceProviderRepositoryPort>()

    private data class Captured(
        val asset: LiveVoiceProviderAsset,
        val apiKey: String?,
    )

    private fun stub(): () -> Captured {
        val box = arrayOf<Captured?>(null)
        coEvery { repository.saveLiveVoiceProvider(any(), any()) } coAnswers {
            box[0] = Captured(firstArg(), secondArg())
            LiveVoiceProviderId("saved-id")
        }
        return { requireNotNull(box[0]) { "saveLiveVoiceProvider was not called" } }
    }

    @Test
    @DisplayName("LV-SAVE-001")
    fun lvSave001_reusesSourceCredentialAliasWhenSet() =
        runTest {
            val capturedFn = stub()

            val result =
                SaveLiveVoiceProviderUseCase(repository)
                    .invoke(
                        LiveVoiceProviderDraft(
                            displayName = "My Live Voice",
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
            assertEquals(LiveVoiceProviderId("saved-id"), result)
        }

    @Test
    @DisplayName("LV-SAVE-002")
    fun lvSave002_fallsBackToDraftAliasAndForwardsApiKey() =
        runTest {
            val capturedFn = stub()

            SaveLiveVoiceProviderUseCase(repository)
                .invoke(
                    LiveVoiceProviderDraft(
                        displayName = "xAI Live Voice",
                        provider = ApiProvider.XAI,
                        voiceName = "eve",
                        credentialAlias = "xai-2",
                        apiKey = "sk-test",
                        sourceCredentialAlias = null,
                    ),
                ).getOrThrow()

            val captured = capturedFn()
            assertEquals("xai-2", captured.asset.credentialAlias)
            assertEquals("sk-test", captured.apiKey)
        }

    @Test
    @DisplayName("LV-SAVE-003")
    fun lvSave003_propagatesModelNameAndVoiceName() =
        runTest {
            val capturedFn = stub()

            SaveLiveVoiceProviderUseCase(repository)
                .invoke(
                    LiveVoiceProviderDraft(
                        displayName = "Google Live Voice",
                        provider = ApiProvider.GOOGLE,
                        voiceName = "Puck",
                        modelName = "gemini-2.0-flash-live-001",
                        credentialAlias = "google",
                        apiKey = "k",
                    ),
                ).getOrThrow()

            val captured = capturedFn()
            assertEquals("gemini-2.0-flash-live-001", captured.asset.modelName)
            assertEquals("Puck", captured.asset.voiceName)
        }
}