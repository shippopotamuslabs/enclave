package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.feature.chat.ArtifactExportSheetState
import com.browntowndev.enclave.feature.chat.ChatModeUi
import com.browntowndev.enclave.feature.chat.ShareState
import com.browntowndev.enclave.feature.chat.mapping.ChatMessageUiMapper
import com.browntowndev.enclave.feature.chat.mapping.PhotoAttachmentPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ChatUiStateReducerOverrideTest {
    private val reducer = ChatUiStateReducer(ChatMessageUiMapper())

    @Test
    @DisplayName("S12 — selectedModeModelName shows override displayName when OTHER")
    fun s12_overrideDisplayNameInSubtitle() {
        val override =
            ChatModelOverride.Api(
                configId = ApiModelConfigurationId("api-grok-beta"),
                displayName = "Grok Beta",
            )
        val result =
            reducer.reduce(
                ChatUiStateReducer.ReduceParams(
                    settings = SettingsData(),
                    inputText = "",
                    selectedMode = ChatModeUi.OTHER,
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
                    shareState = ShareState.Idle,
                    isReadOnly = false,
                    sharedMessages = emptyList(),
                    artifactExportSheet = ArtifactExportSheetState.Hidden,
                    chatId = null,
                    modelDisplayNames = emptyMap(),
                    selectedModelOverride = override,
                ),
            )

        assertEquals("Grok Beta", result.uiState.selectedModeModelName)
    }

    @Test
    @DisplayName("S17 — OTHER excluded from modeModelNames")
    fun s17_otherExcludedFromModeModelNames() {
        val modelDisplayNames =
            mapOf(
                ModelType.FAST to "Slot Fast Name",
                ModelType.THINKING to "Slot Think Name",
            )
        val result =
            reducer.reduce(
                ChatUiStateReducer.ReduceParams(
                    settings = SettingsData(),
                    inputText = "",
                    selectedMode = ChatModeUi.OTHER,
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
                    shareState = ShareState.Idle,
                    isReadOnly = false,
                    sharedMessages = emptyList(),
                    artifactExportSheet = ArtifactExportSheetState.Hidden,
                    chatId = null,
                    modelDisplayNames = modelDisplayNames,
                ),
            )

        assertFalse(result.uiState.modeModelNames.containsKey(ChatModeUi.OTHER))
        assertEquals("Slot Fast Name", result.uiState.modeModelNames[ChatModeUi.FAST])
        assertEquals("Slot Think Name", result.uiState.modeModelNames[ChatModeUi.THINKING])
        assertNull(result.uiState.modeModelNames[ChatModeUi.COUNCIL])
    }
}
