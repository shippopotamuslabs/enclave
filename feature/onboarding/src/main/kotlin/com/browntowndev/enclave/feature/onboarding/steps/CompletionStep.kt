package com.browntowndev.enclave.feature.onboarding.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

@Composable
fun CompletionStep(
    isSaving: Boolean,
    saveError: String?,
    onCompleteOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = "You're all set!",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Enclave is ready. Tap below to start chatting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))

        saveError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isSaving) {
            CircularProgressIndicator()
        } else {
            NeoPrimaryButton(
                text = "Start using Enclave",
                onClick = onCompleteOnboarding,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "CompletionStep — Light", showBackground = true)
@Composable
internal fun CompletionStepLightPreview() {
    EnclaveTheme(darkTheme = false) {
        CompletionStep(
            isSaving = false,
            saveError = null,
            onCompleteOnboarding = {},
        )
    }
}

@Preview(name = "CompletionStep — Dark", showBackground = true)
@Composable
internal fun CompletionStepDarkPreview() {
    EnclaveTheme(darkTheme = true) {
        CompletionStep(
            isSaving = false,
            saveError = "An error occurred",
            onCompleteOnboarding = {},
        )
    }
}
