package com.browntowndev.pocketcrew.feature.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.browntowndev.pocketcrew.feature.chat.R
import com.browntowndev.pocketcrew.feature.chat.ChatModeUi
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.browntowndev.pocketcrew.core.ui.component.ShimmerText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputBar(
    inputText: String,
    speechState: SpeechState,
    selectedImageUri: String?,
    isPhotoAttachmentEnabled: Boolean,
    photoAttachmentDisabledReason: String?,
    selectedMode: ChatModeUi,
    isGenerating: Boolean,
    isGlobalInferenceBlocked: Boolean = false,
    onInputChange: (String) -> Unit,
    onModeChange: (ChatModeUi) -> Unit,
    onSend: (String) -> Unit,
    onStopGenerating: () -> Unit,
    onAttach: () -> Unit,
    onClearAttachment: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var modeExpanded by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onMicClick()
        } else {
            Toast.makeText(context, "Microphone permission is required for speech-to-text", Toast.LENGTH_SHORT).show()
        }
    }

    var textFieldValue by remember { mutableStateOf(TextFieldValue(inputText)) }

    LaunchedEffect(inputText) {
        if (textFieldValue.text != inputText) {
            textFieldValue = TextFieldValue(
                text = inputText,
                selection = TextRange(inputText.length)
            )
        }

        if (inputText.isEmpty()) {
            isExpanded = false
        }
    }

    // Auto-expand: when text exceeds ~60 chars OR has newlines, show expand icon and auto-expand
    val hasNewline = textFieldValue.text.contains('\n')
    val isLongText = textFieldValue.text.length > 60
    val showExpandIcon = hasNewline || isLongText

    // Request focus when expanded
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            focusManager.clearFocus()
            focusRequester.requestFocus()
        }
    }

    // When expanded: unlimited. When collapsed: auto-grow up to 5 lines
    val maxLines = if (isExpanded) Int.MAX_VALUE else 5
    // Always use Enter key - send button handles sending, prevents accidental sends during thinking
    val imeAction = ImeAction.Default

    val isRecordingPhase = speechState is SpeechState.ModelLoading ||
        speechState is SpeechState.Listening ||
        speechState is SpeechState.Transcribing

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isExpanded) Modifier.fillMaxHeight(0.9f) else Modifier.heightIn(min = 56.dp))
            .then(if (!isExpanded && !isRecordingPhase) Modifier.clickable { focusRequester.requestFocus() } else Modifier),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Box(modifier = Modifier.navigationBarsPadding()) {
            val isListening = speechState is SpeechState.ModelLoading || speechState is SpeechState.Listening
            val hasSendableContent = (textFieldValue.text.isNotBlank() || selectedImageUri != null) && !isListening
            val isSendDisabled = (isGenerating || isGlobalInferenceBlocked) && !isGenerating

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isExpanded) Modifier.fillMaxHeight() else Modifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = when {
                    isExpanded -> Arrangement.SpaceBetween
                    else -> Arrangement.spacedBy(6.dp)
                }
            ) {
                // Image preview (Animated visibility to avoid shifts)
                AnimatedVisibility(visible = selectedImageUri != null && !isRecordingPhase) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                    ) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Selected image preview",
                            modifier = Modifier
                                .size(92.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        IconButton(
                            onClick = onClearAttachment,
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove selected image",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                // Text field / Waveform area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isExpanded) Modifier.weight(1f) else Modifier)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Text field
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { newValue ->
                                textFieldValue = newValue
                                onInputChange(newValue.text)
                            },
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 16.sp,
                                lineHeight = 22.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = maxLines,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = imeAction
                            ),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.padding(start = 16.dp, top = 10.dp)) {
                                    if (textFieldValue.text.isEmpty() || isRecordingPhase) {
                                        val stateKey = when (speechState) {
                                            is SpeechState.ModelLoading -> "loading"
                                            is SpeechState.Transcribing -> "transcribing"
                                            is SpeechState.Listening -> "listening"
                                            else -> "idle"
                                        }
                                        AnimatedContent(
                                            targetState = stateKey,
                                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                                            label = "PlaceholderTransition"
                                        ) { target ->
                                            when (target) {
                                                "loading" -> {
                                                    Text(
                                                        text = "Preparing microphone...",
                                                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                "transcribing" -> {
                                                    ShimmerText(
                                                        text = "Transcribing...",
                                                        baseColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        highlightColor = MaterialTheme.colorScheme.primary,
                                                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp)
                                                    )
                                                }
                                                "listening" -> {
                                                    if (speechState is SpeechState.Listening) {
                                                        Box(
                                                            modifier = Modifier
                                                                .height(50.dp)
                                                                .fillMaxWidth()
                                                                .padding(end = 48.dp),
                                                            contentAlignment = Alignment.CenterStart
                                                        ) {
                                                            SoundWave(state = speechState)
                                                        }
                                                    }
                                                }
                                                else -> {
                                                    Text(
                                                        text = "Prompt the Pocket Crew",
                                                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (!isRecordingPhase) {
                                        innerTextField()
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                        )

                        // Collapse / Expand icon
                        if (!isRecordingPhase) {
                            if (isExpanded) {
                                IconButton(onClick = {
                                    isExpanded = !isExpanded
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.collapse_content),
                                        contentDescription = "Collapse input",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (showExpandIcon) {
                                IconButton(onClick = {
                                    isExpanded = !isExpanded
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.expand_content),
                                        contentDescription = "Expand input",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Spacer(Modifier.width(48.dp))
                            }
                        }
                    }
                }

                // ── Fixed bottom action row ──
                AnimatedVisibility(visible = !isRecordingPhase) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Attachment (left-aligned)
                        IconButton(
                            onClick = onAttach,
                            enabled = isPhotoAttachmentEnabled,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.attach_file),
                                contentDescription = "Attach file",
                                tint = if (isPhotoAttachmentEnabled) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                }
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        // ChatModeUi selector
                        ExposedDropdownMenuBox(
                            expanded = modeExpanded,
                            onExpandedChange = { modeExpanded = it }
                        ) {
                            IconButton(
                                onClick = { /* Toggle handled via onExpandedChange */ },
                                modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            ) {
                                Icon(
                                    painter = painterResource(selectedMode.iconRes),
                                    contentDescription = selectedMode.getDisplayName(context),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ExposedDropdownMenu(
                                expanded = modeExpanded,
                                onDismissRequest = { modeExpanded = false },
                                modifier = Modifier.width(200.dp)
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
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // Space for the unified action button
                        Spacer(Modifier.width(48.dp))
                    }
                }

                if (!isRecordingPhase && !isPhotoAttachmentEnabled && !photoAttachmentDisabledReason.isNullOrBlank()) {
                    Text(
                        text = photoAttachmentDisabledReason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                    )
                }
            }

            // Unified Action Button Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isRecordingPhase && speechState is SpeechState.Transcribing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(56.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }

                AnimatedContent(
                    targetState = hasSendableContent && !isGenerating && !isRecordingPhase,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "SendMicTransition"
                ) { isSending ->
                    if (isSending) {
                        IconButton(
                            onClick = {
                                if (hasSendableContent && !isSendDisabled) {
                                    val textToSend = textFieldValue.text
                                    onSend(textToSend)
                                    textFieldValue = TextFieldValue("")
                                    onInputChange("")
                                    isExpanded = false
                                    focusManager.clearFocus()
                                }
                            },
                            enabled = !isSendDisabled
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message",
                                tint = if (isSendDisabled) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    } else {
                        val containerColor = when {
                            isGenerating -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                            speechState is SpeechState.Transcribing -> MaterialTheme.colorScheme.surfaceVariant
                            isRecordingPhase -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }

                        val contentColor = when {
                            isGenerating -> MaterialTheme.colorScheme.error
                            speechState is SpeechState.Transcribing -> MaterialTheme.colorScheme.onSurfaceVariant
                            isRecordingPhase -> MaterialTheme.colorScheme.onErrorContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        val icon = if (isGenerating || isRecordingPhase) Icons.Default.Stop else Icons.Default.Mic
                        val description = when {
                            isGenerating -> "Stop generating"
                            isRecordingPhase -> "Stop recording"
                            else -> "Speech to text"
                        }

                        FilledIconButton(
                            onClick = {
                                if (isGenerating) {
                                    onStopGenerating()
                                } else if (isRecordingPhase) {
                                    onMicClick()
                                } else {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        focusManager.clearFocus()
                                        onMicClick()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            enabled = speechState !is SpeechState.Transcribing,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = containerColor,
                                contentColor = contentColor,
                                disabledContainerColor = containerColor,
                                disabledContentColor = contentColor
                            )
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = description
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundWave(state: SpeechState.Listening, modifier: Modifier = Modifier) {
    var volumes by remember { mutableStateOf(listOf<Float>()) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val silenceColor = MaterialTheme.colorScheme.surfaceVariant

    LaunchedEffect(state) {
        // Amplify the RMS (which is usually around 0.01 - 0.1) for visual effect.
        // 8x multiplier (decreased from 15x) so louder sound is required to max out.
        val amplified = (state.volume * 8f).coerceIn(0f, 1f)
        // Add twice per update to double horizontal scrolling speed
        volumes = (volumes + listOf(amplified, amplified)).takeLast(100)
    }

    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val barWidth = 4.dp.toPx()
        val spacing = 3.dp.toPx()
        val maxBars = (size.width / (barWidth + spacing)).toInt()

        val displayVolumes = volumes.takeLast(maxBars)

        // Calculate startX so bars fill from the right
        val startX = size.width - displayVolumes.size * (barWidth + spacing)

        displayVolumes.forEachIndexed { index, vol ->
            val h = (vol * size.height).coerceIn(4.dp.toPx(), size.height)
            val x = startX + index * (barWidth + spacing)
            val y = (size.height - h) / 2f

            drawRoundRect(
                color = if (vol <= 0.01f) silenceColor else primaryColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

// ==================== PREVIEWS ====================

@Preview
@Composable
fun PreviewInputBar() {
    PocketCrewTheme {
        InputBar(
            inputText = "",
            speechState = SpeechState.Idle,
            selectedImageUri = null,
            isPhotoAttachmentEnabled = true,
            photoAttachmentDisabledReason = null,
            selectedMode = ChatModeUi.FAST,
            isGenerating = false,
            onInputChange = {},
            onModeChange = {},
            onSend = {},
            onStopGenerating = {},
            onAttach = {},
            onClearAttachment = {},
            onMicClick = {},
        )
    }
}

@Preview
@Composable
fun PreviewInputBarExpanded() {
    PocketCrewTheme {
        InputBar(
            inputText = """
                Line 1 of expanded input.
                Line 2.
                Line 3: showing collapse icon.
            """.trimIndent(),
            speechState = SpeechState.Idle,
            selectedImageUri = null,
            isPhotoAttachmentEnabled = false,
            photoAttachmentDisabledReason = "Crew mode requires an API vision model.",
            selectedMode = ChatModeUi.CREW,
            isGenerating = false,
            onInputChange = {},
            onModeChange = {},
            onSend = {},
            onStopGenerating = {},
            onAttach = {},
            onClearAttachment = {},
            onMicClick = {},
        )
    }
}

@Preview
@Composable
fun PreviewInputBarSingleLine() {
    PocketCrewTheme {
        InputBar(
            inputText = "Single line message",
            speechState = SpeechState.Idle,
            selectedImageUri = null,
            isPhotoAttachmentEnabled = true,
            photoAttachmentDisabledReason = null,
            selectedMode = ChatModeUi.FAST,
            isGenerating = false,
            onInputChange = {},
            onModeChange = {},
            onSend = {},
            onStopGenerating = {},
            onAttach = {},
            onClearAttachment = {},
            onMicClick = {},
        )
    }
}

@Preview
@Composable
fun PreviewInputBarThinking() {
    PocketCrewTheme {
        InputBar(
            inputText = "Message while thinking",
            speechState = SpeechState.Idle,
            selectedImageUri = null,
            isPhotoAttachmentEnabled = true,
            photoAttachmentDisabledReason = null,
            selectedMode = ChatModeUi.FAST,
            isGenerating = true,
            onInputChange = {},
            onModeChange = {},
            onSend = {},
            onStopGenerating = {},
            onAttach = {},
            onClearAttachment = {},
            onMicClick = {},
        )
    }
}

@Preview
@Composable
fun PreviewInputBarThinkingMode() {
    PocketCrewTheme {
        InputBar(
            inputText = "Tell me about quantum physics",
            speechState = SpeechState.Idle,
            selectedImageUri = null,
            isPhotoAttachmentEnabled = true,
            photoAttachmentDisabledReason = null,
            selectedMode = ChatModeUi.THINKING,
            isGenerating = false,
            onInputChange = {},
            onModeChange = {},
            onSend = {},
            onStopGenerating = {},
            onAttach = {},
            onClearAttachment = {},
            onMicClick = {},
        )
    }
}

@Preview
@Composable
fun PreviewInputBarStopIndicator() {
    PocketCrewTheme {
        InputBar(
            inputText = "Generating response...",
            speechState = SpeechState.Idle,
            selectedImageUri = null,
            isPhotoAttachmentEnabled = true,
            photoAttachmentDisabledReason = null,
            selectedMode = ChatModeUi.FAST,
            isGenerating = true,
            onInputChange = {},
            onModeChange = {},
            onSend = {},
            onStopGenerating = {},
            onAttach = {},
            onClearAttachment = {},
            onMicClick = {},
        )
    }
}
