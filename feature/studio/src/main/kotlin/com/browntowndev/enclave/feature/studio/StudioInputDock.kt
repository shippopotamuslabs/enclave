package com.browntowndev.enclave.feature.studio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.UniversalImageAttachment
import com.browntowndev.enclave.core.ui.component.UniversalInputBar
import com.browntowndev.enclave.core.ui.component.UniversalPromptTextField
import com.browntowndev.enclave.core.ui.component.UniversalVoiceTrailingAction
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveIconButton
import com.browntowndev.enclave.core.ui.theme.EnclaveThemeAccessor
import dev.chrisbanes.haze.HazeState

@Composable
@Suppress("LongParameterList")
internal fun StudioInputDock(
    prompt: String,
    speechState: StudioSpeechStateUi,
    mediaType: StudioMediaTypeUi,
    referenceImageUri: String?,
    referenceMediaType: StudioMediaTypeUi?,
    isGenerating: Boolean,
    isContinualGenerationActive: Boolean,
    hasImageEditModel: Boolean,
    hasImageToVideoModel: Boolean,
    onPromptChange: (String) -> Unit,
    onReferenceImageClick: () -> Unit,
    onClearReferenceImage: () -> Unit,
    onMicClick: () -> Unit,
    onGenerateClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isExpanded) {
        isExpanded = false
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth(),
    ) {
        UniversalInputBar(
            hazeState = if (EnclaveThemeAccessor.glass.enabled) hazeState else null,
            isExpanded = isExpanded,
            attachmentContent =
                if (referenceImageUri != null) {
                    {
                        UniversalImageAttachment(
                            uri = referenceImageUri,
                            isVideo = referenceMediaType == StudioMediaTypeUi.VIDEO,
                            onClear = onClearReferenceImage,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        )
                    }
                } else {
                    null
                },
            inputContent = {
                StudioPromptField(
                    prompt = prompt,
                    speechState = speechState,
                    isExpanded = isExpanded,
                    onPromptChange = {
                        onPromptChange(it)
                    },
                )
            },
            actionContent =
                if (speechState.isActiveVoiceInput) {
                    null
                } else {
                    {
                        ReferenceImageAction(
                            mediaType = mediaType,
                            hasReferenceImage = referenceImageUri != null,
                            hasImageEditModel = hasImageEditModel,
                            hasImageToVideoModel = hasImageToVideoModel,
                            onReferenceImageClick = onReferenceImageClick,
                        )
                    }
                },
            trailingAction = {
                UniversalVoiceTrailingAction(
                    inputText = prompt,
                    speechState = speechState.toDomain(),
                    isGenerating = isGenerating || isContinualGenerationActive,
                    canStop = true,
                    onSend = { onGenerateClick() },
                    onStop = onGenerateClick,
                    onMicClick = onMicClick,
                    onCollapse = { isExpanded = false },
                    isExpanded = isExpanded,
                    onExpandToggle = { isExpanded = !isExpanded },
                )
            },
        )
    }
}

@Composable
internal fun StudioPromptField(
    prompt: String,
    speechState: StudioSpeechStateUi,
    isExpanded: Boolean,
    onPromptChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    UniversalPromptTextField(
        value = prompt,
        onValueChange = onPromptChange,
        speechState = speechState.toDomain(),
        idlePlaceholder = "Describe what to create...",
        isExpanded = isExpanded,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
internal fun ReferenceImageAction(
    mediaType: StudioMediaTypeUi,
    hasReferenceImage: Boolean,
    hasImageEditModel: Boolean,
    hasImageToVideoModel: Boolean,
    onReferenceImageClick: () -> Unit,
) {
    val isEnabled =
        when (mediaType) {
            StudioMediaTypeUi.IMAGE -> hasImageEditModel
            StudioMediaTypeUi.VIDEO -> hasImageToVideoModel
            else -> false
        }
    val shouldShow = mediaType == StudioMediaTypeUi.IMAGE || mediaType == StudioMediaTypeUi.VIDEO

    if (shouldShow) {
        EnclaveIconButton(
            onClick = onReferenceImageClick,
            icon = if (hasReferenceImage) Icons.Default.Edit else Icons.Default.AddPhotoAlternate,
            contentDescription = "Reference image",
            iconTint = if (isEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            withShadow = isEnabled,
        )
    }
}
