package com.browntowndev.enclave.feature.studio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.StandardTrailingAction
import com.browntowndev.enclave.core.ui.component.UniversalImageAttachment
import com.browntowndev.enclave.core.ui.component.UniversalInputBar
import com.browntowndev.enclave.core.ui.component.UniversalPromptTextField
import com.browntowndev.enclave.core.ui.component.UniversalVoiceTrailingAction
import com.browntowndev.enclave.core.ui.component.neo.NeoIconButton
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.media.isActiveVoiceInput

@Composable
internal fun StudioInputDock(
    prompt: String,
    speechState: SpeechState,
    mediaType: MediaCapability,
    referenceImageUri: String?,
    referenceMediaType: MediaCapability?,
    isGenerating: Boolean,
    isContinualGenerationActive: Boolean,
    onPromptChange: (String) -> Unit,
    onReferenceImageClick: () -> Unit,
    onClearReferenceImage: () -> Unit,
    onSettingsClick: () -> Unit,
    onMicClick: () -> Unit,
    onGenerateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isExpanded) {
        isExpanded = false
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .imePadding(), // Ensure it stays above keyboard
    ) {
        UniversalInputBar(
            isExpanded = isExpanded,
            onExpandToggle = { isExpanded = !isExpanded },
            attachmentContent =
                if (referenceImageUri != null) {
                    {
                        UniversalImageAttachment(
                            uri = referenceImageUri,
                            isVideo = referenceMediaType == MediaCapability.VIDEO,
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
                            onReferenceImageClick = onReferenceImageClick,
                        )
                    }
                },
            trailingAction = {
                if (!speechState.isActiveVoiceInput) {
                    StandardTrailingAction(
                        icon =
                            when (mediaType) {
                                MediaCapability.IMAGE -> Icons.Default.Image
                                MediaCapability.VIDEO -> Icons.Default.Movie
                                MediaCapability.MUSIC -> Icons.Default.MusicNote
                            },
                        onClick = onSettingsClick,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        description = "Studio Options",
                    )
                    Spacer(Modifier.width(8.dp))
                }

                UniversalVoiceTrailingAction(
                    inputText = prompt,
                    speechState = speechState,
                    isGenerating = isGenerating || isContinualGenerationActive,
                    canStop = true,
                    onSend = { onGenerateClick() },
                    onStop = onGenerateClick,
                    onMicClick = onMicClick,
                )
            },
        )
    }
}

@Composable
internal fun StudioPromptField(
    prompt: String,
    speechState: SpeechState,
    isExpanded: Boolean,
    onPromptChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    UniversalPromptTextField(
        value = prompt,
        onValueChange = onPromptChange,
        speechState = speechState,
        idlePlaceholder = "Describe what to create...",
        isExpanded = isExpanded,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
internal fun ReferenceImageAction(
    mediaType: MediaCapability,
    hasReferenceImage: Boolean,
    onReferenceImageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (mediaType != MediaCapability.MUSIC) {
        NeoIconButton(
            onClick = onReferenceImageClick,
            icon = if (hasReferenceImage) Icons.Default.Edit else Icons.Default.AddPhotoAlternate,
            contentDescription = "Reference image",
            modifier = modifier,
        )
    }
}
