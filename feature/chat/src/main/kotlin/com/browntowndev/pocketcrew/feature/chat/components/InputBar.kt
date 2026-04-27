package com.browntowndev.pocketcrew.feature.chat.components

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.browntowndev.pocketcrew.core.ui.component.ShimmerText
import com.browntowndev.pocketcrew.core.ui.component.StandardTrailingAction
import com.browntowndev.pocketcrew.core.ui.component.UniversalInputBar
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.feature.chat.ChatModeUi
import com.browntowndev.pocketcrew.feature.chat.R

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
    canStop: Boolean,
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onMicClick()
        } else {
            Toast.makeText(context, "Microphone permission is required for speech-to-text", Toast.LENGTH_SHORT).show()
        }
    }

    val isListening = speechState is SpeechState.ModelLoading || speechState is SpeechState.Listening
    val isRecordingPhase = isListening || speechState is SpeechState.Transcribing
    val hasSendableContent = (inputText.isNotBlank() || selectedImageUri != null) && !isListening

    UniversalInputBar(
        isExpanded = isExpanded,
        modifier = modifier.then(
            if (!isExpanded && !isRecordingPhase) {
                Modifier.clickable { focusRequester.requestFocus() }
            } else Modifier
        ),
        attachmentContent = {
            AnimatedVisibility(visible = selectedImageUri != null && speechState == SpeechState.Idle) {
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
            
            if (speechState == SpeechState.Idle && !isPhotoAttachmentEnabled && !photoAttachmentDisabledReason.isNullOrBlank()) {
                Text(
                    text = photoAttachmentDisabledReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                )
            }
        },
        inputContent = {
            BasicTextField(
                value = inputText,
                onValueChange = {
                    onInputChange(it)
                    if (!isExpanded && (it.contains("\n") || it.length > 40)) {
                        isExpanded = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (isExpanded) ImeAction.Default else ImeAction.Send
                ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.padding(start = 16.dp, top = 10.dp)) {
                        if (inputText.isEmpty() || isRecordingPhase) {
                            AnimatedContent(
                                targetState = speechState,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "PlaceholderTransition",
                                contentKey = { it::class }
                            ) { target ->
                                when (target) {
                                    is SpeechState.ModelLoading -> {
                                        Text(
                                            text = "Preparing microphone...",
                                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    is SpeechState.Transcribing -> {
                                        ShimmerText(
                                            text = "Transcribing...",
                                            baseColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            highlightColor = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp)
                                        )
                                    }
                                    is SpeechState.Listening -> {
                                        Box(
                                            modifier = Modifier
                                                .height(50.dp)
                                                .fillMaxWidth()
                                                .padding(end = 48.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            SoundWave(state = target)
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
                }
            )
        },
        actionContent = if (isRecordingPhase) null else {
            {
                // Attachment Icon
                IconButton(
                    onClick = onAttach,
                    enabled = isPhotoAttachmentEnabled,
                ) {
                    Icon(
                        painter = painterResource(com.browntowndev.pocketcrew.core.ui.R.drawable.attach_file),
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
            }
        },
        trailingAction = {
            if (speechState is SpeechState.Transcribing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            } else {
                AnimatedContent(
                    targetState = hasSendableContent && !isGenerating && !isRecordingPhase,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "SendMicTransition"
                ) { isSending ->
                    val isRecording = speechState is SpeechState.Listening || speechState is SpeechState.ModelLoading
                    val icon = when {
                        isGenerating || isRecording -> Icons.Default.Stop
                        isSending -> Icons.AutoMirrored.Filled.Send
                        else -> Icons.Default.Mic
                    }
                    
                    val stopEnabled = (isGenerating && canStop) || isRecording
                    val micEnabled = !isGenerating && !isRecording && !isGlobalInferenceBlocked
                    val sendEnabled = isSending && !isGenerating && !isRecording && !isGlobalInferenceBlocked
                    
                    val enabled = stopEnabled || micEnabled || sendEnabled
                    
                    val containerColor = when {
                        isRecording || isGenerating -> MaterialTheme.colorScheme.errorContainer
                        isSending -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val contentColor = when {
                        isRecording || isGenerating -> MaterialTheme.colorScheme.onErrorContainer
                        isSending -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    StandardTrailingAction(
                        onClick = {
                            when {
                                isGenerating -> if (canStop) onStopGenerating()
                                isRecording -> onMicClick()
                                isSending -> {
                                    onSend(inputText)
                                    isExpanded = false
                                }
                                else -> {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        onMicClick()
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            }
                        },
                        icon = icon,
                        enabled = enabled,
                        containerColor = containerColor,
                        contentColor = contentColor
                    )
                }
            }
        }
    )
}

@Composable
private fun SoundWave(state: SpeechState.Listening, modifier: Modifier = Modifier) {
    var volumes by remember { mutableStateOf(listOf<Float>()) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val silenceColor = MaterialTheme.colorScheme.surfaceVariant

    LaunchedEffect(state.volume) {
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

@Preview(showSystemUi = true)
@Composable
fun PreviewChatInputBar() {
    PocketCrewTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            InputBar(
                inputText = "Hello World",
                speechState = SpeechState.Idle,
                selectedImageUri = null,
                isPhotoAttachmentEnabled = true,
                photoAttachmentDisabledReason = null,
                selectedMode = ChatModeUi.FAST,
                isGenerating = false,
                canStop = true,
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
}
