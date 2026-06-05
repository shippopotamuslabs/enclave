package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.usecase.settings.SettingsPreferencesUseCases
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsSearchDelegateTest {
    private val preferencesUseCases = mockk<SettingsPreferencesUseCases>(relaxed = true)
    private val cloudSyncPort = mockk<CloudSyncPort>()
    private val errorHandler = mockk<ViewModelErrorHandler>()
    private val delegate =
        SettingsSearchDelegate(
            preferencesUseCases = preferencesUseCases,
            cloudSyncPort = cloudSyncPort,
            errorHandler = errorHandler,
        )

    @BeforeEach
    fun setup() {
        every {
            errorHandler.coroutineExceptionHandler(any(), any(), any())
        } returns CoroutineExceptionHandler { _, _ -> }
    }

    @Test
    fun `onSaveSearchSkillSettings flushes pending search tool settings to cloud`() =
        runTest {
            every { cloudSyncPort.triggerSync() } returns Unit

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

            verify(exactly = 1) { cloudSyncPort.triggerSync() }
        }
}
