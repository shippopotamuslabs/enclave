package com.browntowndev.pocketcrew.core.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.core.ui.component.ShimmerText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/**
 * A universal container for input bars (Chat, Studio, etc.).
 * Handles the shared "premium" layout, including the bleed-to-bottom background,
 * navigation bar insets, and slot-based vertical structure.
 */
@Composable
fun UniversalInputBar(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    attachmentContent: (@Composable ColumnScope.() -> Unit)? = null,
    inputContent: @Composable ColumnScope.() -> Unit,
    actionContent: (@Composable RowScope.() -> Unit)? = null,
    trailingAction: @Composable (RowScope.() -> Unit)? = null,
    isExpanded: Boolean = false,
    onExpandToggle: (() -> Unit)? = null,
    maxHeightCollapsed: androidx.compose.ui.unit.Dp = 200.dp,
    hazeState: HazeState? = null,
    hazeAlpha: Float = 0.4f,
) {
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = shape
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(
                    if (hazeState != null) {
                        Modifier.hazeEffect(state = hazeState) {
                            blurRadius = 24.dp
                            tints = listOf(HazeTint(Color.Black.copy(alpha = hazeAlpha)))
                            noiseFactor = 0.15f
                        }
                    } else Modifier.background(backgroundColor)
                )
                .navigationBarsPadding()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val contentMaxH = maxHeight
                val density = LocalDensity.current
                var attachmentHeight by remember { mutableStateOf(0.dp) }
                var inputHeight by remember { mutableStateOf(0.dp) }
                val innerTopHeight by remember { derivedStateOf { attachmentHeight + inputHeight } }

                
                val expandFraction by animateFloatAsState(
                    targetValue = if (isExpanded) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = 1.0f,
                        stiffness = 400.0f
                    ),
                    label = "expandFraction"
                )
                
                // Calculate the true collapsed height based on the current unconstrained inner content
                val unconstrainedHeight = 12.dp + innerTopHeight + 56.dp
                val trueCollapsedHeight = unconstrainedHeight.coerceIn(112.dp, maxHeightCollapsed)
                val currentHeight = lerp(trueCollapsedHeight, contentMaxH * 0.7f, expandFraction)
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            when (expandFraction) {
                                0f -> Modifier.heightIn(min = 112.dp, max = maxHeightCollapsed)
                                1f -> Modifier.fillMaxHeight(0.7f) // Reduced from 0.9f to avoid hitting TopAppBar
                                else -> Modifier.height(currentHeight)
                            }
                        )
                        .padding(start = 16.dp, end = 16.dp)
                ) {
                    // TOP CONTENT (Pinned attachments + Scrollable input)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 56.dp) // Reserves space for the bottom row (48dp height + 8dp padding)
                            .then(if (isExpanded || expandFraction > 0f) Modifier.fillMaxHeight() else Modifier.wrapContentHeight()),
                        verticalArrangement = Arrangement.Top
                    ) {
                        // 1. Pinned Attachment Row
                        if (attachmentContent != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { attachmentHeight = with(density) { it.height.toDp() } }
                            ) {
                                attachmentContent()
                            }
                        } else {
                            attachmentHeight = 0.dp
                        }

                        // 2. Scrollable Input Area
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Box(modifier = Modifier.onSizeChanged { inputHeight = with(density) { it.height.toDp() } }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        inputContent()
                                    }

                                    if (onExpandToggle != null) {
                                        // Sufficient space for the top-right chevron
                                        Spacer(Modifier.width(40.dp))
                                    }
                                }
                            }
                        }
                    }

                    // BOTTOM LEFT: Action Content (Attach, Mode, etc)
                    var lastActionContent by remember { mutableStateOf(actionContent) }
                    if (actionContent != null) {
                        lastActionContent = actionContent
                    }

                    AnimatedVisibility(
                        visible = actionContent != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        lastActionContent?.let { content ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .heightIn(min = 48.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                content()
                                
                                // Ensure the trailing area is reserved in the action row too
                                if (trailingAction != null) {
                                    Spacer(Modifier.width(104.dp))
                                }
                            }
                        }
                    }

                    // Top Right: Expand/Collapse Toggle
                    if (onExpandToggle != null) {
                        IconButton(
                            onClick = {
                                if (isExpanded) focusManager.clearFocus()
                                onExpandToggle()
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp)
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Bottom Right: Trailing Container (Mic, Send)
                    if (trailingAction != null) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 8.dp)
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.End
                        ) {
                            this@Row.trailingAction()
                        }
                    }
                }
            } // End BoxWithConstraints
        }
    }
}

/**
 * A standardized circular action button for use in [UniversalInputBar]'s trailingAction slot.
 */
@Composable
fun StandardTrailingAction(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
    shape: Shape = CircleShape,
    description: String? = null,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .background(if (enabled) containerColor else disabledContainerColor, shape = shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) contentColor else disabledContentColor
        )
    }
}

/**
 * A standardized circular action button for use in [UniversalInputBar]'s trailingAction slot.
 */
@Composable
fun StandardTrailingAction(
    painter: androidx.compose.ui.graphics.painter.Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    disabledContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
    shape: Shape = CircleShape,
    description: String? = null,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .background(if (enabled) containerColor else disabledContainerColor, shape = shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = description,
            tint = if (enabled) contentColor else disabledContentColor
        )
    }
}

/**
 * A shared placeholder that handles the transitions between idle, preparing, listening, and transcribing states.
 */
@Composable
fun UniversalVoicePromptPlaceholder(
    speechState: SpeechState,
    idlePlaceholder: String = "Prompt the Pocket Crew",
    modifier: Modifier = Modifier,
) {
    val isRecordingPhase = speechState is SpeechState.Listening || 
                          speechState is SpeechState.ModelLoading || 
                          speechState is SpeechState.Transcribing

    Box(modifier = modifier) {
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
                        text = idlePlaceholder,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * A shared trailing action that handles swapping between Mic, Send, and Stop buttons
 * based on the current speech and generation states.
 */
@Composable
fun UniversalVoiceTrailingAction(
    inputText: String,
    speechState: SpeechState,
    isGenerating: Boolean,
    canStop: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
    isGlobalInferenceBlocked: Boolean = false,
    hasSendableContent: Boolean = inputText.isNotBlank(),
) {
    val isRecordingPhase = speechState is SpeechState.Listening || 
                          speechState is SpeechState.ModelLoading || 
                          speechState is SpeechState.Transcribing

    if (speechState is SpeechState.Transcribing) {
        Box(
            modifier = modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        }
    } else {
        AnimatedContent(
            targetState = hasSendableContent && !isGenerating && !isRecordingPhase,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "SendMicTransition",
            modifier = modifier
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
                        isGenerating -> if (canStop) onStop()
                        isRecording -> onMicClick()
                        isSending -> onSend(inputText)
                        else -> onMicClick()
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

@Composable
fun SoundWave(state: SpeechState.Listening, modifier: Modifier = Modifier) {
    var volumes by remember { mutableStateOf(listOf<Float>()) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val silenceColor = MaterialTheme.colorScheme.surfaceVariant

    LaunchedEffect(state.volume) {
        val amplified = (state.volume * 8f).coerceIn(0f, 1f)
        volumes = (volumes + listOf(amplified, amplified)).takeLast(100)
    }

    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val barWidth = 4.dp.toPx()
        val spacing = 3.dp.toPx()
        val maxBars = (size.width / (barWidth + spacing)).toInt()

        val displayVolumes = volumes.takeLast(maxBars)
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