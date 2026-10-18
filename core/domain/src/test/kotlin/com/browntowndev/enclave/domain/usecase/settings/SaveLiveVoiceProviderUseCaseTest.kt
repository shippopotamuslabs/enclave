package com.browntowndev.enclave.domain.usecase.settings

import com.browntowndev.enclave.core.testing.FakeLiveVoiceProviderRepository
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SaveLiveVoiceProviderUseCaseTest {
    private val repository = FakeLiveVoiceProviderRepository()

    @Test
    @DisplayName("LV-SAVE-001")
    fun lvSave001_reusesSourceCredentialAliasWhenSet() =
        runTest {
            repository.saveResultId = LiveVoiceProviderId("saved-id")

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

            val captured = repository.savedCalls.single()
            assertEquals("openai", captured.first.credentialAlias)
            assertNull(captured.second)
            assertEquals(LiveVoiceProviderId("saved-id"), result)
        }

    @Test
    @DisplayName("LV-SAVE-002")
    fun lvSave002_fallsBackToDraftAliasAndForwardsApiKey() =
        runTest {
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

            val captured = repository.savedCalls.single()
            assertEquals("xai-2", captured.first.credentialAlias)
            assertEquals("sk-test", captured.second)
        }

    @Test
    @DisplayName("LV-SAVE-003")
    fun lvSave003_propagatesModelNameAndVoiceName() =
        runTest {
            SaveLiveVoiceProviderUseCase(repository)
                .invoke(
                    LiveVoiceProviderDraft(
                        displayName = "Google Live Voice",
                        provider = ApiProvider.GOOGLE,
                        voiceName = "Puck",
                        modelName = "gemini-2.0-flash-live-001",
                        credentialAlias = "google",
                        apiKey = "",
                    ),
                ).getOrThrow()

            val captured = repository.savedCalls.single()
            assertEquals("gemini-2.0-flash-live-001", captured.first.modelName)
            assertEquals("Puck", captured.first.voiceName)
        }
}
