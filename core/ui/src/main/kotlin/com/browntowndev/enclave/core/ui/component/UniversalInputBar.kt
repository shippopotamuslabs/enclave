package com.browntowndev.enclave.core.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.browntowndev.enclave.core.ui.R
import com.browntowndev.enclave.core.ui.component.ShimmerText
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveCircularProgressIndicator
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveDisabledActionTooltip
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveIconButton
import com.browntowndev.enclave.core.ui.component.enclave.rememberNeoDisabledClick
import com.browntowndev.enclave.core.ui.component.enclave.enclaveBorderColor
import com.browntowndev.enclave.core.ui.component.enclave.enclaveSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveThemeAccessor
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.media.isActiveVoiceInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * A universal container for input bars (Chat, Studio, etc.).
 * Handles the shared "premium" layout, including the bleed-to-bottom background,
 * navigation bar insets, and slot-based vertical structure.
 *
 * Neo-brutalist borders, shadows, and icon treatments are intentional for every Enclave
 * input dock built on this component; feature modules should not swap them for flat M3 chrome.
 */
@Composable
fun UniversalInputBar(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    shape: Shape = EnclaveNeoTokens.inputBarShape,
    attachmentContent: (@Composable ColumnScope.() -> Unit)? = null,
    inputContent: @Composable ColumnScope.() -> Unit,
    actionContent: (@Composable RowScope.() -> Unit)? = null,
    trailingAction: @Composable (RowScope.() -> Unit)? = null,
    isExpanded: Boolean = false,
    maxHeightCollapsed: androidx.compose.ui.unit.Dp = 200.dp,
    hazeState: HazeState? = null,
    hazeAlpha: Float = 0.4f,
) {
    val focusManager = LocalFocusManager.current
    val borderColor = enclaveBorderColor()
    val glass = EnclaveThemeAccessor.glass

    // Surface matches the bar face so the dock reads full-bleed edge-to-edge.
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeEffect(state = hazeState) {
                                blurRadius = if (glass.enabled) glass.blurRadius else 24.dp
                                tints =
                                    listOf(
                                        HazeTint(
                                            if (glass.enabled) {
                                                glass.tint
                                            } else {
                                                Color.Black.copy(alpha = hazeAlpha)
                                            },
                                        ),
                                    )
                                noiseFactor = if (glass.enabled) glass.noiseFactor else 0.15f
                            }
                        } else {
                            Modifier.enclaveSurface(
                                backgroundColor = backgroundColor,
                                borderColor = borderColor,
                                withShadow = false,
                                shape = shape,
                            )
                        },
                    ),
        ) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            ) {
                val contentMaxH = maxHeight
                val density = LocalDensity.current
                var attachmentHeight by remember { mutableStateOf(0.dp) }
                var inputHeight by remember { mutableStateOf(0.dp) }
                val inputScrollState = rememberScrollState()
                val innerTopHeight by remember { derivedStateOf { attachmentHeight + inputHeight } }

                LaunchedEffect(inputHeight) {
                    inputScrollState.scrollTo(Int.MAX_VALUE)
                }

                val expandFraction by animateFloatAsState(
                    targetValue = if (isExpanded) 1f else 0f,
                    animationSpec =
                        spring(
                            dampingRatio = 1.0f,
                            stiffness = 400.0f,
                        ),
                    label = "expandFraction",
                )

                // Calculate the true collapsed height based on the current unconstrained inner content
                val unconstrainedHeight =
                    InputBarLayoutMetrics.topPadding + innerTopHeight + InputBarLayoutMetrics.bottomRowReserve
                val trueCollapsedHeight =
                    unconstrainedHeight.coerceIn(InputBarLayoutMetrics.minCollapsedHeight, maxHeightCollapsed)
                val currentHeight = lerp(trueCollapsedHeight, contentMaxH * 0.7f, expandFraction)

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .then(
                                when (expandFraction) {
                                    0f ->
                                        Modifier.heightIn(
                                            min = InputBarLayoutMetrics.minCollapsedHeight,
                                            max = maxHeightCollapsed,
                                        )
                                    1f -> Modifier.fillMaxHeight(0.7f) // Reduced from 0.9f to avoid hitting TopAppBar
                                    else -> Modifier.height(currentHeight)
                                },
                            ).padding(start = 16.dp, end = 16.dp),
                ) {
                    // TOP CONTENT (Pinned attachments + Scrollable input)
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = InputBarLayoutMetrics.topPadding,
                                    bottom = InputBarLayoutMetrics.bottomRowReserve,
                                ).then(if (isExpanded || expandFraction > 0f) Modifier.fillMaxHeight() else Modifier.wrapContentHeight()),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        // 1. Pinned Attachment Row
                        if (attachmentContent != null) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .onSizeChanged { attachmentHeight = with(density) { it.height.toDp() } },
                            ) {
                                attachmentContent()
                            }
                        } else {
                            attachmentHeight = 0.dp
                        }

                        // 2. Scrollable Input Area
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScroll(inputScrollState),
                        ) {
                            Box(modifier = Modifier.onSizeChanged { inputHeight = with(density) { it.height.toDp() } }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Column(
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .padding(bottom = 8.dp),
                                    ) {
                                        inputContent()
                                    }
                                }
                            }
                        }
                    }

                    // BOTTOM LEFT: Action Content (Attach, Mode, etc)
                    Row(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .animateContentSize()
                                .padding(bottom = InputBarLayoutMetrics.actionRowBottomPadding)
                                .heightIn(
                                    min =
                                        if (actionContent != null) {
                                            InputBarLayoutMetrics.actionRowHeight
                                        } else {
                                            0.dp
                                        },
                                ),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        if (actionContent != null) {
                            actionContent()

                            if (trailingAction != null) {
                                Spacer(Modifier.width(InputBarLayoutMetrics.trailingActionReserve))
                            }
                        }
                    }


                    // Bottom Right: Trailing Container (Mic, Send)
                    if (trailingAction != null) {
                        Row(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = InputBarLayoutMetrics.actionRowBottomPadding)
                                    .heightIn(min = InputBarLayoutMetrics.actionRowHeight),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.End,
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
    description: String? = null,
    withShadow: Boolean = true,
) {
    EnclaveIconButton(
        onClick = onClick,
        icon = icon,
        contentDescription = description,
        modifier = modifier,
        enabled = enabled,
        size = EnclaveNeoTokens.trailingActionSize,
        backgroundColor = if (enabled) containerColor else disabledContainerColor,
        iconTint = if (enabled) contentColor else disabledContentColor,
        borderColor = enclaveBorderColor(),
        withShadow = withShadow && enabled,
    )
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
    description: String? = null,
    withShadow: Boolean = true,
) {
    EnclaveIconButton(
        onClick = onClick,
        icon = painter,
        contentDescription = description,
        modifier = modifier,
        enabled = enabled,
        size = EnclaveNeoTokens.trailingActionSize,
        backgroundColor = if (enabled) containerColor else disabledContainerColor,
        iconTint = if (enabled) contentColor else disabledContentColor,
        borderColor = enclaveBorderColor(),
        withShadow = withShadow && enabled,
    )
}

/**
 * A shared placeholder that handles the transitions between idle, preparing, listening, and transcribing states.
 */
@Composable
fun UniversalVoicePromptPlaceholder(
    speechState: SpeechState,
    idlePlaceholder: String = "Enter the Enclave",
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AnimatedContent(
            targetState = speechState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "PlaceholderTransition",
            contentKey = { it::class },
        ) { target ->
            when (target) {
                is SpeechState.ModelLoading -> {
                    Text(
                        text = "Preparing microphone...",
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is SpeechState.Transcribing -> {
                    ShimmerText(
                        text = "Transcribing...",
                        baseColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        highlightColor = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                    )
                }
                is SpeechState.Listening -> {
                    Box(
                        modifier =
                            Modifier
                                .height(50.dp)
                                .fillMaxWidth()
                                .padding(end = 48.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        SoundWave(state = target)
                    }
                }
                else -> {
                    Text(
                        text = idlePlaceholder,
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
@Suppress("CyclomaticComplexMethod", "LongParameterList")
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
    onCollapse: (() -> Unit)? = null,
    onLiveVoiceClick: (() -> Unit)? = null,
    isLiveVoiceEnabled: Boolean = true,
    isExpanded: Boolean = false,
    onExpandToggle: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    if (speechState is SpeechState.Transcribing) {
        Box(
            modifier = modifier.size(EnclaveNeoTokens.trailingActionSize),
            contentAlignment = Alignment.Center,
        ) {
            EnclaveCircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    } else {
        val isRecording = speechState is SpeechState.Listening || speechState is SpeechState.ModelLoading
        val isSending = hasSendableContent && !isGenerating && !speechState.isActiveVoiceInput
        val showLiveVoiceButton =
            onLiveVoiceClick != null &&
                !isSending &&
                !isGenerating &&
                !speechState.isActiveVoiceInput

        Row(
            modifier = modifier.animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = isSending && onExpandToggle != null,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                val expandCollapseIcon = painterResource(
                    id = if (isExpanded) R.drawable.collapse_content else R.drawable.expand_content
                )
                StandardTrailingAction(
                    painter = expandCollapseIcon,
                    onClick = {
                        if (isExpanded) focusManager.clearFocus()
                        onExpandToggle?.invoke()
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    description = if (isExpanded) "Collapse" else "Expand",
                )
            }
            AnimatedContent(
                targetState = isSending,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "SendMicTransition",
            ) { sending ->
                val icon =
                    when {
                        isGenerating || isRecording -> Icons.Default.Stop
                        sending -> Icons.AutoMirrored.Filled.Send
                        else -> Icons.Default.Mic
                    }

                val stopEnabled = (isGenerating && canStop) || isRecording
                val micEnabled = !isGenerating && !isRecording && !isGlobalInferenceBlocked
                val sendEnabled = sending && !isGenerating && !isRecording && !isGlobalInferenceBlocked

                val enabled = stopEnabled || micEnabled || sendEnabled

                val containerColor =
                    when {
                        isRecording || isGenerating -> MaterialTheme.colorScheme.errorContainer
                        sending -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                val contentColor =
                    when {
                        isRecording || isGenerating -> MaterialTheme.colorScheme.onErrorContainer
                        sending -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                val description =
                    when {
                        isGenerating || isRecording -> "Stop"
                        sending -> "Send"
                        else -> "Voice input"
                    }

                StandardTrailingAction(
                    onClick = {
                        when {
                            isGenerating -> if (canStop) onStop()
                            isRecording -> onMicClick()
                            sending -> {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onCollapse?.invoke()
                                onSend(inputText)
                            }
                            else -> onMicClick()
                        }
                    },
                    icon = icon,
                    enabled = enabled,
                    containerColor = containerColor,
                    contentColor = contentColor,
                    withShadow = enabled,
                    description = description,
                )
            }

            if (showLiveVoiceButton) {
                EnclaveDisabledActionTooltip(
                    enabled = isLiveVoiceEnabled,
                    disabledReason = if (isLiveVoiceEnabled) null else "Assign a Live Voice provider in Settings",
                ) {
                    val handleLiveVoiceClick =
                        rememberNeoDisabledClick(
                            enabled = isLiveVoiceEnabled,
                            onEnabledClick = { onLiveVoiceClick() },
                        )
                    StandardTrailingAction(
                        onClick = handleLiveVoiceClick,
                        icon = Icons.Default.GraphicEq,
                        enabled = isLiveVoiceEnabled,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        withShadow = isLiveVoiceEnabled,
                        description = "Live voice",
                    )
                }
            }
        }
    }
}

@Composable
fun SoundWave(
    state: SpeechState.Listening,
    modifier: Modifier = Modifier,
) {
    var volumes by remember { mutableStateOf(listOf<Float>()) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val silenceColor = MaterialTheme.colorScheme.surfaceVariant

    LaunchedEffect(state.volume) {
        val amplified = (state.volume * 8f).coerceIn(0f, 1f)
        volumes = (volumes + listOf(amplified, amplified)).takeLast(100)
    }

    Canvas(
        modifier = modifier.fillMaxSize(),
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
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

/**
 * A standardized thumbnail for image or video attachments in an input bar.
 */
@Composable
fun UniversalImageAttachment(
    uri: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    isVideo: Boolean = false,
) {
    Box(
        modifier =
            modifier
                .size(80.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (isVideo) {
            VideoThumbnail(
                videoUri = uri,
                contentDescription = contentDescription ?: "Reference video thumbnail",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AsyncImage(
                model = uri,
                contentDescription = contentDescription ?: "Attached image preview",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = CircleShape,
            modifier =
                Modifier
                    .padding(4.dp)
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .clickable(onClick = onClear),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear attachment",
                tint = Color.White,
                modifier = Modifier.padding(4.dp),
            )
        }
    }
}
