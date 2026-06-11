package com.browntowndev.enclave.feature.chat.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoIconButton
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.neoBorderColor
import com.browntowndev.enclave.core.ui.component.neo.neoSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.feature.chat.LiveVoiceUiState
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun LiveVoiceOverlay(
    state: LiveVoiceUiState,
    onEnd: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listeningState = state as? LiveVoiceUiState.Listening
    val speakingState = state as? LiveVoiceUiState.AssistantSpeaking
    val userCaption = listeningState?.userCaption ?: speakingState?.userCaption.orEmpty()
    val assistantCaption = listeningState?.assistantCaption ?: speakingState?.assistantCaption.orEmpty()
    val isMuted = listeningState?.isMuted ?: speakingState?.isMuted ?: false
    val rawAudioLevel = listeningState?.micLevel ?: speakingState?.audioLevel ?: 0f
    val isConnecting = state is LiveVoiceUiState.Connecting
    val errorState = state as? LiveVoiceUiState.Error
    val isAssistantSpeaking = speakingState != null
    val toolStatusCaption = listeningState?.toolStatusCaption ?: speakingState?.toolStatusCaption
    val isAudioMotionActive =
        when {
            isConnecting || errorState != null -> false
            isAssistantSpeaking -> true
            listeningState != null -> !isMuted
            else -> false
        }
    val audioMotion =
        rememberLiveVoiceAudioMotion(
            audioLevel = rawAudioLevel,
            isActive = isAudioMotionActive,
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                LiveVoiceOrb(
                    isConnecting = isConnecting,
                    isSpeaking = isAssistantSpeaking,
                    pulseLevel = audioMotion.displayLevel,
                )

                LiveVoiceWaveform(
                    volumes = audioMotion.waveVolumes,
                    wavePhase = audioMotion.wavePhase,
                    isActive = isAudioMotionActive,
                    isAssistantSpeaking = isAssistantSpeaking,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(horizontal = 16.dp),
                )

                Text(
                    text =
                        when {
                            isConnecting -> "Connecting..."
                            errorState != null -> errorState.message
                            isAssistantSpeaking -> "Assistant speaking"
                            isMuted -> "Muted"
                            else -> "Listening"
                        },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (toolStatusCaption != null) {
                Text(
                    text = toolStatusCaption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LiveVoiceCaptionCard(
                    label = "You",
                    text = userCaption,
                )
                LiveVoiceCaptionCard(
                    label = "Assistant",
                    text = assistantCaption,
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NeoIconButton(
                    onClick = onToggleMute,
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = if (isMuted) "Unmute microphone" else "Mute microphone",
                    size = 52.dp,
                )
                NeoPrimaryButton(
                    text = "End",
                    onClick = onEnd,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private data class LiveVoiceAudioMotion(
    val displayLevel: Float,
    val waveVolumes: List<Float>,
    val wavePhase: Int,
)

@Composable
private fun rememberLiveVoiceAudioMotion(
    audioLevel: Float,
    isActive: Boolean,
): LiveVoiceAudioMotion {
    val latestLevel = rememberUpdatedState(audioLevel)
    var displayLevel by remember { mutableFloatStateOf(0f) }
    var waveVolumes by remember { mutableStateOf(emptyList<Float>()) }
    var wavePhase by remember { mutableIntStateOf(0) }

    LaunchedEffect(isActive) {
        if (!isActive) {
            displayLevel = 0f
            waveVolumes = emptyList()
            wavePhase = 0
            return@LaunchedEffect
        }
        var smoothedLevel = 0f
        while (isActive) {
            val target = (latestLevel.value * LIVE_VOICE_LEVEL_GAIN).coerceIn(0f, 1f)
            smoothedLevel += (target - smoothedLevel) * LEVEL_SMOOTHING_ALPHA
            displayLevel = smoothedLevel
            waveVolumes = (waveVolumes + smoothedLevel).takeLast(WAVE_HISTORY_SIZE)
            wavePhase += 1
            delay(WAVE_SAMPLE_INTERVAL_MS)
        }
    }

    return LiveVoiceAudioMotion(
        displayLevel = displayLevel,
        waveVolumes = waveVolumes,
        wavePhase = wavePhase,
    )
}

@Composable
private fun LiveVoiceOrb(
    isConnecting: Boolean,
    isSpeaking: Boolean,
    pulseLevel: Float,
    modifier: Modifier = Modifier,
) {
    val connectingPulse =
        if (isConnecting) {
            val infiniteTransition = rememberInfiniteTransition(label = "liveVoiceConnectingPulse")
            infiniteTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.85f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 900),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "liveVoiceConnectingPulseValue",
            ).value
        } else {
            null
        }
    val targetPulse =
        when {
            connectingPulse != null -> connectingPulse
            isSpeaking -> pulseLevel.coerceIn(SILENCE_ORB_PULSE, 1f)
            else -> pulseLevel.coerceIn(MIN_LISTENING_ORB_PULSE, 1f)
        }
    val pulse by animateFloatAsState(
        targetValue = targetPulse,
        animationSpec = tween(durationMillis = if (isSpeaking) ORB_SPEAKING_ANIMATION_MS else ORB_PULSE_ANIMATION_MS),
        label = "liveVoiceOrbPulse",
    )
    val orbColor =
        when {
            isSpeaking -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.primary
        }

    Box(
        modifier =
            modifier
                .size(ORB_OUTER_SIZE_DP.dp)
                .clip(CircleShape)
                .neoSurface(
                    backgroundColor = orbColor.copy(alpha = ORB_RING_ALPHA),
                    borderColor = neoBorderColor(),
                    withShadow = true,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size((ORB_INNER_MIN_DP + pulse * ORB_INNER_RANGE_DP).dp)
                    .clip(CircleShape)
                    .background(orbColor.copy(alpha = ORB_INNER_ALPHA)),
        )
    }
}

@Composable
private fun LiveVoiceWaveform(
    volumes: List<Float>,
    wavePhase: Int,
    isActive: Boolean,
    isAssistantSpeaking: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeColor =
        if (isAssistantSpeaking) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.primary
        }
    val silenceColor = MaterialTheme.colorScheme.surfaceVariant
    val connectingPulse =
        if (!isActive) {
            val infiniteTransition = rememberInfiniteTransition(label = "liveVoiceIdleWavePulse")
            infiniteTransition.animateFloat(
                initialValue = 0.04f,
                targetValue = 0.14f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 700),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "liveVoiceIdleWavePulseValue",
            ).value
        } else {
            null
        }

    Canvas(modifier = modifier.fillMaxSize()) {
        val barWidth = 4.dp.toPx()
        val spacing = 3.dp.toPx()
        val maxBars = (size.width / (barWidth + spacing)).toInt().coerceAtLeast(1)
        val displayVolumes =
            if (isActive) {
                volumes.takeLast(maxBars)
            } else {
                List(maxBars) { index ->
                    val phase = index.toFloat() / maxBars
                    (connectingPulse ?: IDLE_WAVE_LEVEL) * (0.6f + phase * 0.4f)
                }
            }
        val startX = size.width - displayVolumes.size * (barWidth + spacing)

        displayVolumes.forEachIndexed { index, volume ->
            val modulatedVolume = modulateBarVolume(volume, index, wavePhase)
            val h = (modulatedVolume * size.height).coerceIn(4.dp.toPx(), size.height)
            val x = startX + index * (barWidth + spacing)
            val y = (size.height - h) / 2f

            drawRoundRect(
                color = if (modulatedVolume > SILENCE_THRESHOLD) activeColor else silenceColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

@Composable
private fun LiveVoiceCaptionCard(
    label: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .neoSurface(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    borderColor = neoBorderColor(),
                    withShadow = true,
                    shape = EnclaveNeoTokens.shape,
                ).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text.ifBlank { "..." },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Start,
        )
    }
}

private fun modulateBarVolume(
    volume: Float,
    barIndex: Int,
    wavePhase: Int,
): Float {
    val wobble = sin(barIndex * 0.9f + wavePhase * 0.32f)
    val multiplier = 0.55f + ((wobble + 1f) * 0.225f)
    return (volume * multiplier).coerceIn(0f, 1f)
}

private const val LIVE_VOICE_LEVEL_GAIN = 8f
private const val WAVE_SAMPLE_INTERVAL_MS = 50L
private const val WAVE_HISTORY_SIZE = 100
private const val LEVEL_SMOOTHING_ALPHA = 0.55f
private const val ORB_PULSE_ANIMATION_MS = 80
private const val ORB_SPEAKING_ANIMATION_MS = 45
private const val ORB_OUTER_SIZE_DP = 120
private const val ORB_INNER_MIN_DP = 40
private const val ORB_INNER_RANGE_DP = 48
private const val ORB_RING_ALPHA = 0.22f
private const val ORB_INNER_ALPHA = 0.9f
private const val SILENCE_ORB_PULSE = 0.04f
private const val MIN_LISTENING_ORB_PULSE = 0.1f
private const val SILENCE_THRESHOLD = 0.01f
private const val IDLE_WAVE_LEVEL = 0.04f

@Preview(showBackground = true, name = "Live Voice Overlay Listening")
@Composable
@Suppress("UnusedPrivateMember")
private fun LiveVoiceOverlayListeningPreview() {
    EnclaveTheme {
        LiveVoiceOverlay(
            state =
                LiveVoiceUiState.Listening(
                    userCaption = "Turn on the lights",
                    assistantCaption = "",
                    isMuted = false,
                    micLevel = 0.6f,
                ),
            onEnd = {},
            onToggleMute = {},
        )
    }
}

@Preview(showBackground = true, name = "Live Voice Overlay Assistant Speaking")
@Composable
@Suppress("UnusedPrivateMember")
private fun LiveVoiceOverlayAssistantSpeakingPreview() {
    EnclaveTheme {
        LiveVoiceOverlay(
            state =
                LiveVoiceUiState.AssistantSpeaking(
                    userCaption = "Tell me a joke",
                    assistantCaption = "Why did the developer go broke?",
                    isMuted = false,
                    audioLevel = 0.45f,
                ),
            onEnd = {},
            onToggleMute = {},
        )
    }
}

@Preview(showBackground = true, name = "Live Voice Overlay Connecting")
@Composable
@Suppress("UnusedPrivateMember")
private fun LiveVoiceOverlayConnectingPreview() {
    EnclaveTheme {
        LiveVoiceOverlay(
            state = LiveVoiceUiState.Connecting,
            onEnd = {},
            onToggleMute = {},
        )
    }
}
