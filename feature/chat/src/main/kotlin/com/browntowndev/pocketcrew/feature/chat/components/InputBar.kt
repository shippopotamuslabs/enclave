package com.browntowndev.pocketcrew.feature.chat.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertPhoto
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.browntowndev.pocketcrew.core.ui.component.StandardTrailingAction
import com.browntowndev.pocketcrew.core.ui.component.UniversalImageAttachment
import com.browntowndev.pocketcrew.core.ui.component.UniversalInputBar
import com.browntowndev.pocketcrew.core.ui.component.UniversalVoicePromptPlaceholder
import com.browntowndev.pocketcrew.core.ui.component.UniversalVoiceTrailingAction
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.feature.chat.ChatModeUi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
fun InputBar(
    inputText: String,
    speechState: SpeechState,
    selectedImageUris: List<String>,
    selectedFileName: String?,
    @Suppress("UnusedParameter")
    isPhotoAttachmentEnabled: Boolean,
    @Suppress("UnusedParameter")
    photoAttachmentMaxItems: Int,
    photoAttachmentDisabledReason: String?,
    selectedMode: ChatModeUi,
    isGenerating: Boolean,
    canStop: Boolean,
    isGlobalInferenceBlocked: Boolean = false,
    onInputChange: (String) -> Unit,
    onModeChange: (ChatModeUi) -> Unit,
    onSend: (String) -> Unit,
    onStopGenerating: () -> Unit,
    onImageAttach: () -> Unit,
    onFileAttach: () -> Unit,
    onClearImage: (String) -> Unit,
    onClearFile: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var modeExpanded by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                onMicClick()
            } else {
                Toast.makeText(context, "Microphone permission is required for speech-to-text", Toast.LENGTH_SHORT).show()
            }
        }

    val isListening = speechState is SpeechState.ModelLoading || speechState is SpeechState.Listening
    val isRecordingPhase = isListening || speechState is SpeechState.Transcribing

    if (showAttachmentSheet) {
        JumpFreeModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                AttachmentMenuItem(
                    icon = Icons.Default.InsertPhoto,
                    label = "Attach Photo",
                    onClick = {
                        showAttachmentSheet = false
                        onImageAttach()
                    },
                    enabled = isPhotoAttachmentEnabled,
                    disabledReason = photoAttachmentDisabledReason,
                )
                AttachmentMenuItem(
                    icon = Icons.Default.AttachFile,
                    label = "Attach File",
                    onClick = {
                        showAttachmentSheet = false
                        onFileAttach()
                    },
                )
            }
        }
    }

    UniversalInputBar(
        isExpanded = isExpanded,
        onExpandToggle = { isExpanded = !isExpanded },
        modifier =
            modifier.then(
                if (!isExpanded && !isRecordingPhase) {
                    Modifier.clickable { focusRequester.requestFocus() }
                } else {
                    Modifier
                },
            ),
        attachmentContent = {
            AnimatedVisibility(visible = selectedImageUris.isNotEmpty() && speechState == SpeechState.Idle) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selectedImageUris.forEach { uri ->
                        UniversalImageAttachment(
                            uri = uri,
                            onClear = { onClearImage(uri) },
                        )
                    }
                }
            }

            AnimatedVisibility(visible = selectedFileName != null && speechState == SpeechState.Idle) {
                Row(
                    modifier =
                        Modifier
                            .padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp),
                            ).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = selectedFileName ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove file",
                        modifier =
                            Modifier
                                .size(16.dp)
                                .clickable { onClearFile() },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        inputContent = {
            BasicTextField(
                value = inputText,
                onValueChange = {
                    onInputChange(it)
                },
                modifier =
                    Modifier
                        .padding(vertical = 5.dp)
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                textStyle =
                    TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                    ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.padding(start = 16.dp, top = 4.dp)) {
                        if (inputText.isEmpty() || isRecordingPhase) {
                            UniversalVoicePromptPlaceholder(
                                speechState = speechState,
                                idlePlaceholder = "Prompt the Pocket Crew",
                            )
                        }
                        if (!isRecordingPhase) {
                            innerTextField()
                        }
                    }
                },
            )
        },
        actionContent =
            if (isRecordingPhase) {
                null
            } else {
                {
                    // Plus Button
                    IconButton(
                        onClick = { showAttachmentSheet = true },
                        modifier = Modifier.clip(CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Show attachment options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        trailingAction = {
            if (!isRecordingPhase) {
                // ChatModeUi selector
                ExposedDropdownMenuBox(
                    expanded = modeExpanded,
                    onExpandedChange = { modeExpanded = it },
                ) {
                    StandardTrailingAction(
                        painter = painterResource(selectedMode.iconRes),
                        onClick = { /* Toggle handled via onExpandedChange */ },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        description = selectedMode.getDisplayName(context),
                    )
                    ExposedDropdownMenu(
                        expanded = modeExpanded,
                        onDismissRequest = { modeExpanded = false },
                        modifier = Modifier.width(200.dp),
                    ) {
                        ChatModeUi.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.getDisplayName(context)) },
                                onClick = {
                                    onModeChange(mode)
                                    modeExpanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(mode.iconRes),
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
            }

            UniversalVoiceTrailingAction(
                inputText = inputText,
                speechState = speechState,
                isGenerating = isGenerating,
                canStop = canStop,
                onSend = onSend,
                onStop = onStopGenerating,
                onMicClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        onMicClick()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                isGlobalInferenceBlocked = isGlobalInferenceBlocked,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    disabledReason: String? = null,
) {
    val scope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState(isPersistent = true)

    val content = @Composable {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (enabled) {
                            onClick()
                        } else if (!disabledReason.isNullOrBlank()) {
                            scope.launch { tooltipState.show() }
                        }
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
            )
        }
    }

    if (!enabled && !disabledReason.isNullOrBlank()) {
        TooltipBox(
            positionProvider =
                TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                ),
            tooltip = {
                PlainTooltip {
                    Text(disabledReason)
                }
            },
            state = tooltipState,
            content = content,
        )
    } else {
        content()
    }
}

@Preview(showSystemUi = true)
@Composable
fun PreviewChatInputBar() {
    PocketCrewTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            InputBar(
                inputText = "Hello World",
                speechState = SpeechState.Idle,
                selectedImageUris = emptyList<String>(),
                selectedFileName = "test_file.txt",
                isPhotoAttachmentEnabled = true,
                photoAttachmentMaxItems = 1,
                photoAttachmentDisabledReason = null,
                selectedMode = ChatModeUi.FAST,
                isGenerating = false,
                canStop = true,
                isGlobalInferenceBlocked = false,
                onInputChange = {},
                onModeChange = {},
                onSend = {},
                onStopGenerating = {},
                onImageAttach = {},
                onFileAttach = {},
                onClearImage = {},
                onClearFile = {},
                onMicClick = {},
            )
        }
    }
}
