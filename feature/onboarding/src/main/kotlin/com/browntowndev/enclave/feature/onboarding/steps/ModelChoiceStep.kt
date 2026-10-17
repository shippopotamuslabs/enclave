@file:Suppress("LongParameterList", "MagicNumber")

package com.browntowndev.enclave.feature.onboarding.steps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.enclave.core.ui.component.enclave.EnclavePanel
import com.browntowndev.enclave.core.ui.component.enclave.EnclavePrimaryButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSecondaryButton
import com.browntowndev.enclave.core.ui.component.enclave.enclaveBorderColor
import com.browntowndev.enclave.core.ui.component.enclave.enclaveSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.feature.onboarding.ApiProviderUi
import com.browntowndev.enclave.feature.onboarding.DiscoveredApiModelUi
import com.browntowndev.enclave.feature.onboarding.ModelChoice

@Composable
fun ModelChoiceStep(
    selectedModelChoice: ModelChoice,
    selectedProvider: ApiProviderUi?,
    apiKey: String,
    baseUrl: String,
    discoveredModels: List<DiscoveredApiModelUi>,
    selectedModelId: String,
    isDiscoveringModels: Boolean,
    discoveryError: String?,
    onModelChoiceSelected: (ModelChoice) -> Unit,
    onProviderSelected: (ApiProviderUi) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onDiscoverModels: () -> Unit,
    onModelSelected: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = enclaveBorderColor()
    val scrollState = rememberScrollState()
    var showConfigureSheet by remember { mutableStateOf(false) }

    val canContinue =
        when (selectedModelChoice) {
            ModelChoice.LOCAL -> true
            ModelChoice.API -> selectedProvider != null
            else -> false
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = EnclaveNeoTokens.screenHorizontalPadding, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Scrollable content area
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "CHOOSE YOUR MODEL SOURCE",
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    ),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Local Models card
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .enclaveSurface(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            borderColor =
                                if (selectedModelChoice == ModelChoice.LOCAL) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    borderColor
                                },
                        ).clickable { onModelChoiceSelected(ModelChoice.LOCAL) }
                        .semantics { contentDescription = "Use Local Models" }
                        .padding(16.dp),
            ) {
                Text(
                    text = "Use Local Models",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color =
                        if (selectedModelChoice == ModelChoice.LOCAL) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Runs entirely on your device. Secure, private, and no internet or API key required.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // API Provider card
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .enclaveSurface(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            borderColor =
                                if (selectedModelChoice == ModelChoice.API) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    borderColor
                                },
                        ).clickable { onModelChoiceSelected(ModelChoice.API) }
                        .semantics { contentDescription = "Use an API Provider" }
                        .padding(16.dp),
            ) {
                Text(
                    text = "Use an API Provider",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color =
                        if (selectedModelChoice == ModelChoice.API) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text =
                        "Connect to premium cloud-based providers (like OpenAI, xAI, " +
                            "OpenRouter) for advanced capabilities.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (selectedModelChoice == ModelChoice.API) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "API PROVIDER CONFIGURATION",
                    style =
                        MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                        ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Provider selection panel
                EnclavePanel(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ApiProviderUi.entries.filter(ApiProviderUi::supportsChat).forEach { provider ->
                            val isSelected = selectedProvider == provider
                            val label =
                                if (provider == ApiProviderUi.OPENROUTER) {
                                    "${provider.displayName} (Recommended)"
                                } else {
                                    provider.displayName
                                }
                            val bgSelected = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .enclaveSurface(
                                            backgroundColor = if (isSelected) bgSelected else MaterialTheme.colorScheme.surface,
                                            borderColor = if (isSelected) MaterialTheme.colorScheme.primary else borderColor,
                                        ).clickable {
                                            onProviderSelected(provider)
                                            if (isSelected) {
                                                showConfigureSheet = true
                                            }
                                        }.semantics { contentDescription = label }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = label,
                                    style =
                                        MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                if (isSelected) {
                                    val isConfigured = apiKey.isNotBlank() && selectedModelId.isNotBlank()
                                    Text(
                                        text = if (isConfigured) "Configured" else "Tap to configure",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Bottom Navigation Bar
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EnclaveSecondaryButton(
                text = "BACK",
                onClick = onPrevious,
                modifier = Modifier.weight(1f),
            )
            EnclavePrimaryButton(
                text = "CONTINUE",
                onClick = {
                    if (selectedModelChoice == ModelChoice.API) {
                        if (apiKey.isBlank() || selectedModelId.isBlank()) {
                            showConfigureSheet = true
                        } else {
                            onNext()
                        }
                    } else {
                        onNext()
                    }
                },
                enabled = canContinue,
                modifier = Modifier.weight(1.5f),
            )
        }
    }

    if (showConfigureSheet && selectedProvider != null) {
        ApiProviderConfigureBottomSheet(
            provider = selectedProvider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            discoveredModels = discoveredModels,
            selectedModelId = selectedModelId,
            isDiscoveringModels = isDiscoveringModels,
            discoveryError = discoveryError,
            onApiKeyChanged = onApiKeyChanged,
            onBaseUrlChanged = onBaseUrlChanged,
            onDiscoverModels = onDiscoverModels,
            onModelSelected = onModelSelected,
            onDismiss = { showConfigureSheet = false },
            onSave = {
                showConfigureSheet = false
                onNext()
            },
        )
    }
}

@Preview(name = "ModelChoiceStep — Light", showBackground = true)
@Composable
internal fun ModelChoiceStepLightPreview() {
    EnclaveTheme(darkTheme = false) {
        ModelChoiceStep(
            selectedModelChoice = ModelChoice.LOCAL,
            selectedProvider = null,
            apiKey = "",
            baseUrl = "",
            discoveredModels = emptyList(),
            selectedModelId = "",
            isDiscoveringModels = false,
            discoveryError = null,
            onModelChoiceSelected = {},
            onProviderSelected = {},
            onApiKeyChanged = {},
            onBaseUrlChanged = {},
            onDiscoverModels = {},
            onModelSelected = {},
            onNext = {},
            onPrevious = {},
        )
    }
}

@Preview(name = "ModelChoiceStep — Dark", showBackground = true)
@Composable
internal fun ModelChoiceStepDarkPreview() {
    EnclaveTheme(darkTheme = true) {
        ModelChoiceStep(
            selectedModelChoice = ModelChoice.API,
            selectedProvider = ApiProviderUi.OPENROUTER,
            apiKey = "sk-test",
            baseUrl = "",
            discoveredModels = listOf(DiscoveredApiModelUi(id = "openrouter/gpt-4o")),
            selectedModelId = "openrouter/gpt-4o",
            isDiscoveringModels = false,
            discoveryError = null,
            onModelChoiceSelected = {},
            onProviderSelected = {},
            onApiKeyChanged = {},
            onBaseUrlChanged = {},
            onDiscoverModels = {},
            onModelSelected = {},
            onNext = {},
            onPrevious = {},
        )
    }
}
