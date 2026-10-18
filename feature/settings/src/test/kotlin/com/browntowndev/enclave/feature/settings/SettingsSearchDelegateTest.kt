package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.usecase.settings.SettingsPreferencesUseCases
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsSearchDelegateTest {
    private val preferencesUseCases = FakeSettingsPreferencesUseCases()
    private val cloudSyncPort = com.browntowndev.enclave.core.testing.FakeCloudSyncPort()
    private val errorHandler = FakeViewModelErrorHandler()
    private val delegate =
        SettingsSearchDelegate(
            preferencesUseCases = preferencesUseCases,
            cloudSyncPort = cloudSyncPort,
            errorHandler = errorHandler,
        )

    @BeforeEach
    fun setup() {
    }

    @Test
    fun `onSaveSearchSkillSettings flushes pending search tool settings to cloud`() =
        runTest {
            val transient = MutableStateFlow(SearchSkillTransientState(enabled = true, selectedProvider = SearchProvider.TAVILY))
            val editor =
                SearchSkillEditorUiState(
                    enabled = false,
                    selectedProvider = SearchProvider.EXA,
                    tavilyKeyPresent = true,
                    exaKeyPresent = false,
                )

            delegate.onSaveSearchSkillSettings(
                scope = this,
                tag = "test",
                searchSkillEditorState = editor,
                transientSearchSkillState = transient,
                currentTavilyApiKey = MutableStateFlow(""),
                currentExaApiKey = MutableStateFlow(""),
                onSuccess = {},
            )

            advanceUntilIdle()

            assertEquals(1, cloudSyncPort.count("triggerSync"))
        }
}
