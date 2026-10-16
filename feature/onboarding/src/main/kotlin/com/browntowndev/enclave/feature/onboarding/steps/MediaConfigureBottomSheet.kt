package com.browntowndev.enclave.feature.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveApiKeyField
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveModalBottomSheet
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveOutlinedTextField
import com.browntowndev.enclave.core.ui.component.enclave.EnclavePrimaryButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSheetHeader
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.usecase.settings.MediaProviderDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaConfigureBottomSheet(
    initialCapability: MediaCapability,
    onDismiss: () -> Unit,
    onSave: (MediaProviderDraft) -> Unit,
) {
    var provider by remember { mutableStateOf(ApiProvider.XAI) }
    var apiKey by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var capability by remember { mutableStateOf(initialCapability) }
    var providerExpanded by remember { mutableStateOf(false) }
    var capabilityExpanded by remember { mutableStateOf(false) }

    val isSaveEnabled = displayName.isNotBlank() && modelId.isNotBlank() && apiKey.isNotBlank()

    EnclaveModalBottomSheet(onDismissRequest = onDismiss) {
        EnclaveSheetHeader(
            title = "Configure ${initialCapability.displayName} Generation",
            subtitle = "Set up ${initialCapability.displayName.lowercase()} generation for your assistant.",
            onDismiss = onDismiss,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.sectionSpacing),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ExposedDropdownMenuBox(
                expanded = providerExpanded,
                onExpandedChange = { providerExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                EnclaveOutlinedTextField(
                    value = provider.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("API Provider") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                    modifier =
                        Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = providerExpanded,
                    onDismissRequest = { providerExpanded = false },
                ) {
                    listOf(ApiProvider.OPENAI, ApiProvider.XAI, ApiProvider.GOOGLE, ApiProvider.FAL).forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.displayName) },
                            onClick = {
                                provider = p
                                providerExpanded = false
                            },
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = capabilityExpanded,
                onExpandedChange = { capabilityExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                EnclaveOutlinedTextField(
                    value = capability.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Capability") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = capabilityExpanded) },
                    modifier =
                        Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = capabilityExpanded,
                    onDismissRequest = { capabilityExpanded = false },
                ) {
                    MediaCapability.entries.forEach { cap ->
                        DropdownMenuItem(
                            text = { Text(cap.displayName) },
                            onClick = {
                                capability = cap
                                capabilityExpanded = false
                            },
                        )
                    }
                }
            }

            EnclaveApiKeyField(
                label = "API Key",
                value = apiKey,
                onValueChange = { apiKey = it },
                keyPresent = false,
                modifier = Modifier.fillMaxWidth(),
            )

            EnclaveOutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name (e.g. xAI Images)") },
                modifier = Modifier.fillMaxWidth(),
            )

            EnclaveOutlinedTextField(
                value = modelId,
                onValueChange = { modelId = it },
                label = { Text("Model ID") },
                modifier = Modifier.fillMaxWidth(),
            )

            EnclavePrimaryButton(
                text = "Save Media Provider",
                onClick = {
                    onSave(
                        MediaProviderDraft(
                            id = MediaProviderId(""),
                            displayName = displayName,
                            provider = provider,
                            modelId = modelId,
                            capability = capability,
                            credentialAlias = provider.name.lowercase(),
                            apiKey = apiKey,
                        ),
                    )
                    onDismiss()
                },
                enabled = isSaveEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
