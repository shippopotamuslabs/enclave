package com.browntowndev.enclave.feature.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.enclave.core.ui.component.neo.NeoGroupDivider
import com.browntowndev.enclave.core.ui.component.neo.NeoPanel
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.feature.onboarding.OnboardingViewModel

@Composable
fun WelcomeAgeGateStep(
    ageConfirmed: Boolean,
    onAgeConfirmedChange: (Boolean) -> Unit,
    automaticErrorTrackingEnabled: Boolean,
    onAutomaticErrorTrackingChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            NeoPanel(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                ) {
                    Text(
                        text = "WELCOME TO ENCLAVE",
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your models. Your keys. Zero-Knowledge encrypted.",
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    NeoGroupDivider(modifier = Modifier.padding(horizontal = 0.dp))
                    Spacer(modifier = Modifier.height(24.dp))

                    AgeGateCheckboxRow(
                        checked = ageConfirmed,
                        onCheckedChange = onAgeConfirmedChange,
                        label = "I confirm I am 18 or older",
                        contentDescription = OnboardingViewModel.AGE_GATE_CONTENT_DESCRIPTION,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AgeGateCheckboxRow(
                        checked = automaticErrorTrackingEnabled,
                        onCheckedChange = onAutomaticErrorTrackingChange,
                        label = "Allow automatic tracking of errors",
                        contentDescription = OnboardingViewModel.AUTOMATIC_ERROR_TRACKING_CONTENT_DESCRIPTION,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text =
                            "Optional. When enabled, Enclave may send error diagnostics to help " +
                                "improve stability. Your chat content stays encrypted on device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        NeoPrimaryButton(
            text = "CONTINUE",
            onClick = onNext,
            enabled = ageConfirmed,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AgeGateCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    contentDescription: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    role = Role.Checkbox
                    this.contentDescription = contentDescription
                },
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline,
                ),
            modifier =
                Modifier.semantics {
                    role = Role.Checkbox
                    this.contentDescription = contentDescription
                },
        )
        Text(
            text = label,
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Preview(name = "WelcomeAgeGateStep — Light", showBackground = true)
@Composable
internal fun WelcomeAgeGateStepLightPreview() {
    EnclaveTheme(darkTheme = false) {
        WelcomeAgeGateStep(
            ageConfirmed = false,
            onAgeConfirmedChange = {},
            automaticErrorTrackingEnabled = false,
            onAutomaticErrorTrackingChange = {},
            onNext = {},
        )
    }
}

@Preview(name = "WelcomeAgeGateStep — Dark", showBackground = true)
@Composable
internal fun WelcomeAgeGateStepDarkPreview() {
    EnclaveTheme(darkTheme = true) {
        WelcomeAgeGateStep(
            ageConfirmed = true,
            onAgeConfirmedChange = {},
            automaticErrorTrackingEnabled = true,
            onAutomaticErrorTrackingChange = {},
            onNext = {},
        )
    }
}
