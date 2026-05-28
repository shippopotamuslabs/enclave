@file:Suppress("MagicNumber")
package com.browntowndev.enclave.feature.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSecondaryButton
import com.browntowndev.enclave.core.ui.component.neo.neoBorderColor
import com.browntowndev.enclave.core.ui.component.neo.neoSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.usecase.settings.MediaProviderDraft
import com.browntowndev.enclave.domain.usecase.settings.TtsProviderDraft

@Suppress("UnusedParameter")
@Composable
fun MediaStep(
    isTtsConfigured: Boolean,
    isMediaConfigured: Boolean,
    onSaveTtsProvider: (TtsProviderDraft) -> Unit,
    onSaveMediaProvider: (MediaProviderDraft) -> Unit,
    onSkipMedia: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val borderColor = neoBorderColor()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Scrollable content area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "MEDIA & OPTIONAL PLUGINS",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Customize your private assistant with optional capabilities. " +
                    "These can also be configured or changed anytime later in Settings.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Showcase list
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // TTS Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neoSurface(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            borderColor = borderColor,
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Text-to-Speech (TTS)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Generates high-quality voices using xAI, enabling Enclave " +
                            "to read messages and interact with you audibly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Image / Video Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neoSurface(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            borderColor = borderColor,
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Image & Video Generation",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Allows you to dynamically create beautiful images and videos " +
                            "directly inside your chats via xAI integration.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Music Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neoSurface(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            borderColor = borderColor,
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Music Generation",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Enables dynamic music track creation in response to your prompts " +
                            "powered by Google AI model capabilities.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Bottom Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NeoSecondaryButton(
                text = "BACK",
                onClick = onPrevious,
                modifier = Modifier.weight(1f)
            )
            NeoPrimaryButton(
                text = "CONTINUE",
                onClick = onNext,
                modifier = Modifier.weight(1.5f)
            )
        }
    }
}

@Preview(name = "MediaStep — Light", showBackground = true)
@Composable
internal fun MediaStepLightPreview() {
    EnclaveTheme(darkTheme = false) {
        MediaStep(
            isTtsConfigured = false,
            isMediaConfigured = false,
            onSaveTtsProvider = {},
            onSaveMediaProvider = {},
            onSkipMedia = {},
            onNext = {},
            onPrevious = {},
        )
    }
}

@Preview(name = "MediaStep — Dark", showBackground = true)
@Composable
internal fun MediaStepDarkPreview() {
    EnclaveTheme(darkTheme = true) {
        MediaStep(
            isTtsConfigured = true,
            isMediaConfigured = false,
            onSaveTtsProvider = {},
            onSaveMediaProvider = {},
            onSkipMedia = {},
            onNext = {},
            onPrevious = {},
        )
    }
}
