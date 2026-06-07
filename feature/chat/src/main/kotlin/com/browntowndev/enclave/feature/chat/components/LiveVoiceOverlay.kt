package com.browntowndev.enclave.feature.chat.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    val micLevel = listeningState?.micLevel ?: 0f
    val isConnecting = state is LiveVoiceUiState.Connecting
    val isError = state is LiveVoiceUiState.Error

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
                    isSpeaking = speakingState != null,
                    micLevel = micLevel,
                )

                BlockyLiveVoiceWaveform(
                    micLevel = micLevel,
                    isActive = listeningState != null && !isMuted,
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
                            isError -> (state as LiveVoiceUiState.Error).message
                            speakingState != null -> "Assistant speaking"
                            isMuted -> "Muted"
                            else -> "Listening"
                        },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
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

@Composable
private fun LiveVoiceOrb(
    isConnecting: Boolean,
    isSpeaking: Boolean,
    micLevel: Float,
    modifier: Modifier = Modifier,
) {
    val pulse by animateFloatAsState(
        targetValue = if (isConnecting) 0.4f else micLevel.coerceIn(0.15f, 1f),
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
                .size(120.dp)
                .clip(CircleShape)
                .neoSurface(
                    backgroundColor = orbColor.copy(alpha = 0.2f + pulse * 0.35f),
                    borderColor = neoBorderColor(),
                    withShadow = true,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size((56 + pulse * 24).dp)
                    .clip(CircleShape)
                    .background(orbColor.copy(alpha = 0.85f)),
        )
    }
}

@Composable
private fun BlockyLiveVoiceWaveform(
    micLevel: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val barHeights =
        remember(micLevel, isActive) {
            List(BLOCKY_BAR_COUNT) { index ->
                val phase = (index.toFloat() / BLOCKY_BAR_COUNT)
                val base = if (isActive) micLevel.coerceIn(0.08f, 1f) else 0.08f
                (base * (0.45f + phase * 0.55f)).coerceIn(0.08f, 1f)
            }
        }
    val primaryColor = MaterialTheme.colorScheme.primary
    val silenceColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        val barWidth = 6.dp.toPx()
        val spacing = 4.dp.toPx()
        val totalWidth = BLOCKY_BAR_COUNT * barWidth + (BLOCKY_BAR_COUNT - 1) * spacing
        var x = (size.width - totalWidth) / 2f

        barHeights.forEach { heightFraction ->
            val barHeight = (size.height * heightFraction).coerceAtLeast(6.dp.toPx())
            val y = (size.height - barHeight) / 2f
            drawRoundRect(
                color = if (isActive && heightFraction > 0.1f) primaryColor else silenceColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
            x += barWidth + spacing
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

private const val BLOCKY_BAR_COUNT = 16

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
