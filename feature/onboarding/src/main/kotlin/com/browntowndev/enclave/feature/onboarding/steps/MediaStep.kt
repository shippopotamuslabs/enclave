package com.browntowndev.enclave.feature.onboarding.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = "Want image, video, or TTS?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "These are optional. You can configure them later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "TTS (via xAI)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Text-to-speech using xAI voices.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Image / Video (via xAI)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Generate images and video with xAI.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Music (via Google)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Generate music with Google AI.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onSkipMedia) {
            Text("Skip")
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
        )
    }
}
