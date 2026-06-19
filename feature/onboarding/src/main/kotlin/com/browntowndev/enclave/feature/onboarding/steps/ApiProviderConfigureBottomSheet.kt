package com.browntowndev.enclave.feature.onboarding.steps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoApiKeyField
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoOutlinedTextField
import com.browntowndev.enclave.core.ui.component.neo.NeoPanel
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.component.neo.neoBorderColor
import com.browntowndev.enclave.core.ui.component.neo.neoSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiProviderConfigureBottomSheet(
    provider: ApiProvider,
    apiKey: String,
    baseUrl: String,
    discoveredModels: List<DiscoveredApiModel>,
    selectedModelId: String,
    isDiscoveringModels: Boolean,
    discoveryError: String?,
    onApiKeyChanged: (String) -> Unit,
    onBaseUrlChanged: (String) -> Unit,
    onDiscoverModels: () -> Unit,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = neoBorderColor()

    NeoModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        NeoSheetHeader(
            title = "Configure ${provider.displayName}",
            subtitle = "Set up API credentials and select a model preset.",
            onDismiss = onDismiss,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.sectionSpacing),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
        ) {
            NeoApiKeyField(
                label = "Enter API Key",
                value = apiKey,
                onValueChange = onApiKeyChanged,
                keyPresent = false,
                modifier = Modifier.fillMaxWidth(),
            )

            NeoOutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChanged,
                label = { Text("Base URL (Optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            NeoPrimaryButton(
                text = if (isDiscoveringModels) "DISCOVERING..." else "DISCOVER MODELS",
                onClick = onDiscoverModels,
                enabled = !isDiscoveringModels && apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            discoveryError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            if (discoveredModels.isNotEmpty()) {
                Text(
                    text = "SELECT MODEL",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )

                NeoPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        discoveredModels.forEach { model ->
                            val isSelected = selectedModelId == model.id
                            val bgSelected = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .neoSurface(
                                            backgroundColor = if (isSelected) bgSelected else MaterialTheme.colorScheme.surface,
                                            borderColor = if (isSelected) MaterialTheme.colorScheme.primary else borderColor,
                                        ).clickable { onModelSelected(model.id) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = model.id,
                                    style =
                                        MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            NeoPrimaryButton(
                text = "SAVE & CONTINUE",
                onClick = onSave,
                enabled = apiKey.isNotBlank() && selectedModelId.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
