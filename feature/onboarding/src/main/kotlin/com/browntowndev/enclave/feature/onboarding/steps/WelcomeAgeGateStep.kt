package com.browntowndev.enclave.feature.onboarding.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.feature.onboarding.OnboardingViewModel

@Composable
fun WelcomeAgeGateStep(
    ageConfirmed: Boolean,
    onAgeConfirmedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = "Welcome to Enclave",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Your private AI assistant.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    role = Role.Checkbox
                    contentDescription = OnboardingViewModel.AGE_GATE_CONTENT_DESCRIPTION
                },
        ) {
            Checkbox(
                checked = ageConfirmed,
                onCheckedChange = onAgeConfirmedChange,
                modifier = Modifier.semantics {
                    role = Role.Checkbox
                    contentDescription = OnboardingViewModel.AGE_GATE_CONTENT_DESCRIPTION
                },
            )
            Text(
                text = "I confirm I am 18 or older",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Preview(name = "WelcomeAgeGateStep — Light", showBackground = true)
@Composable
internal fun WelcomeAgeGateStepLightPreview() {
    EnclaveTheme(darkTheme = false) {
        WelcomeAgeGateStep(ageConfirmed = false, onAgeConfirmedChange = {})
    }
}

@Preview(name = "WelcomeAgeGateStep — Dark", showBackground = true)
@Composable
internal fun WelcomeAgeGateStepDarkPreview() {
    EnclaveTheme(darkTheme = true) {
        WelcomeAgeGateStep(ageConfirmed = true, onAgeConfirmedChange = {})
    }
}
