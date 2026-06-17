package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.feature.chat.ChatModeUi
import com.browntowndev.enclave.feature.chat.mapping.ChatMessageUiMapper
import com.browntowndev.enclave.feature.chat.mapping.PhotoAttachmentPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.api.Test

class ChatUiStateReducerLiveVoiceModelNameTest {
    private val reducer = ChatUiStateReducer(ChatMessageUiMapper())

    private val modelDisplayNames =
        mapOf(
            ModelType.FAST to "Grok Fast",
            ModelType.THINKING to "Grok Think",
            ModelType.FINAL_SYNTHESIS to "Grok Final",
        )

    @ParameterizedTest
    @CsvSource(
        "FAST, Grok Fast",
        "THINKING, Grok Think",
    )
    @DisplayName("LV-MODELNAME-001/002: reducer computes selectedModeModelName per mode")
    fun lvModelName_modeMapsToDisplayName(
        mode: ChatModeUi,
        expected: String,
    ) {
        val result = reduce(selectedMode = mode)

        assertEquals(expected, result.uiState.selectedModeModelName)
    }

    @Test
    @DisplayName("LV-MODELNAME-003: Council shows no model name (fans out to multiple models)")
    fun lvModelName003_councilHasNoModelName() {
        val result = reduce(selectedMode = ChatModeUi.COUNCIL)

        assertNull(result.uiState.selectedModeModelName)
    }

    @Test
    @DisplayName("LV-MODELNAME-005: modeModelNames carries Fast/Thinking but omits Council")
    fun lvModelName005_modeModelNamesPerMode() {
        val result = reduce(selectedMode = ChatModeUi.FAST)

        assertEquals("Grok Fast", result.uiState.modeModelNames[ChatModeUi.FAST])
        assertEquals("Grok Think", result.uiState.modeModelNames[ChatModeUi.THINKING])
        assertNull(result.uiState.modeModelNames[ChatModeUi.COUNCIL])
    }

    @Test
    @DisplayName("LV-MODELNAME-004: missing map entry yields null model name")
    fun lvModelName004_missingEntryReturnsNull() {
        val result =
            reducer.reduce(
                ChatUiStateReducer.ReduceParams(
                    settings = SettingsData(),
                    inputText = "",
                    selectedMode = ChatModeUi.FAST,
                    selectedImageUris = emptyList(),
                    selectedFileName = null,
                    speechState = SpeechState.Idle,
                    attachmentPolicy =
                        PhotoAttachmentPolicy(
                            isEnabled = false,
                            maxItems = 0,
                            disabledReason = null,
                        ),
                    isGlobalInferenceBlocked = false,
                    dbMessages = emptyList(),
                    activeTurnKey = null,
                    activeTurnSnapshot = null,
                    activeToolCallBanner = null,
                    hasTtsProviderAssigned = false,
                    isPlayingTts = false,
                    studioMediaMap = emptyMap(),
                    shareState = com.browntowndev.enclave.feature.chat.ShareState.Idle,
                    isReadOnly = false,
                    sharedMessages = emptyList(),
                    artifactExportSheet = com.browntowndev.enclave.feature.chat.ArtifactExportSheetState.Hidden,
                    chatId = null,
                    modelDisplayNames = mapOf(ModelType.THINKING to "Grok Think"),
                ),
            )

        assertNull(result.uiState.selectedModeModelName)
    }

    private fun reduce(selectedMode: ChatModeUi) =
        reducer.reduce(
            ChatUiStateReducer.ReduceParams(
                settings = SettingsData(),
                inputText = "",
                selectedMode = selectedMode,
                selectedImageUris = emptyList(),
                selectedFileName = null,
                speechState = SpeechState.Idle,
                attachmentPolicy =
                    PhotoAttachmentPolicy(
                        isEnabled = false,
                        maxItems = 0,
                        disabledReason = null,
                    ),
                isGlobalInferenceBlocked = false,
                dbMessages = emptyList(),
                activeTurnKey = null,
                activeTurnSnapshot = null,
                activeToolCallBanner = null,
                hasTtsProviderAssigned = false,
                isPlayingTts = false,
                studioMediaMap = emptyMap(),
                shareState = com.browntowndev.enclave.feature.chat.ShareState.Idle,
                isReadOnly = false,
                sharedMessages = emptyList(),
                artifactExportSheet = com.browntowndev.enclave.feature.chat.ArtifactExportSheetState.Hidden,
                chatId = null,
                modelDisplayNames = modelDisplayNames,
            ),
        )
}