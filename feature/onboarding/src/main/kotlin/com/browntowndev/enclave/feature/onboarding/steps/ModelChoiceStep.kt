package com.browntowndev.enclave.feature.onboarding.steps

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoApiKeyField
import com.browntowndev.enclave.core.ui.component.neo.neoBorderColor
import com.browntowndev.enclave.core.ui.component.neo.neoSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.feature.onboarding.ModelChoice

@Composable
fun ModelChoiceStep(
    selectedModelChoice: ModelChoice,
    selectedProvider: ApiProvider?,
    apiKey: String,
    discoveredModels: List<DiscoveredApiModel>,
    selectedModelId: String,
    isDiscoveringModels: Boolean,
    discoveryError: String?,
    onModelChoiceSelected: (ModelChoice) -> Unit,
    onProviderSelected: (ApiProvider) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onDiscoverModels: () -> Unit,
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = neoBorderColor()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
    ) {
        Text(
            text = "Choose Your Model Source",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Local Models card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neoSurface(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    borderColor = if (selectedModelChoice == ModelChoice.LOCAL) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        borderColor
                    },
                )
                .clickable { onModelChoiceSelected(ModelChoice.LOCAL) }
                .semantics { contentDescription = "Use Local Models" }
                .padding(16.dp),
        ) {
            Text(
                text = "Use Local Models",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Runs entirely on your device. No API key needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // API Provider card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neoSurface(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    borderColor = if (selectedModelChoice == ModelChoice.API) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        borderColor
                    },
                )
                .clickable { onModelChoiceSelected(ModelChoice.API) }
                .semantics { contentDescription = "Use an API Provider" }
                .padding(16.dp),
        ) {
            Text(
                text = "Use an API Provider",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Connect to a cloud provider for richer capabilities.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "You can always change this later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (selectedModelChoice == ModelChoice.API) {
            Spacer(modifier = Modifier.height(16.dp))

            // Provider picker
            ApiProvider.entries.forEach { provider ->
                val label = if (provider == ApiProvider.OPENROUTER) {
                    "${provider.displayName} (Recommended)"
                } else {
                    provider.displayName
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedProvider == provider) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    modifier = Modifier
                        .clickable { onProviderSelected(provider) }
                        .semantics { contentDescription = label }
                        .padding(vertical = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            NeoApiKeyField(
                value = apiKey,
                onValueChange = onApiKeyChanged,
                label = "API Key",
                keyPresent = false,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onDiscoverModels) {
                Text(if (isDiscoveringModels) "Discovering…" else "Discover Models")
            }

            discoveryError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (discoveredModels.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                discoveredModels.forEach { model ->
                    Text(
                        text = model.id,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedModelId == model.id) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModelSelected(model.id) }
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
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
            discoveredModels = emptyList(),
            selectedModelId = "",
            isDiscoveringModels = false,
            discoveryError = null,
            onModelChoiceSelected = {},
            onProviderSelected = {},
            onApiKeyChanged = {},
            onDiscoverModels = {},
            onModelSelected = {},
        )
    }
}

@Preview(name = "ModelChoiceStep — Dark", showBackground = true)
@Composable
internal fun ModelChoiceStepDarkPreview() {
    EnclaveTheme(darkTheme = true) {
        ModelChoiceStep(
            selectedModelChoice = ModelChoice.API,
            selectedProvider = ApiProvider.OPENROUTER,
            apiKey = "sk-test",
            discoveredModels = listOf(DiscoveredApiModel(id = "openrouter/gpt-4o")),
            selectedModelId = "openrouter/gpt-4o",
            isDiscoveringModels = false,
            discoveryError = null,
            onModelChoiceSelected = {},
            onProviderSelected = {},
            onApiKeyChanged = {},
            onDiscoverModels = {},
            onModelSelected = {},
        )
    }
}
