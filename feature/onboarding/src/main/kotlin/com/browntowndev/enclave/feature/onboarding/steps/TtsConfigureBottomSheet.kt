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
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.TtsVoices
import com.browntowndev.enclave.domain.usecase.settings.TtsProviderDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsConfigureBottomSheet(
    onDismiss: () -> Unit,
    onSave: (TtsProviderDraft) -> Unit,
) {
    var provider by remember { mutableStateOf(ApiProvider.XAI) }
    var apiKey by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var voiceName by remember { mutableStateOf("") }
    var providerExpanded by remember { mutableStateOf(false) }
    var voiceExpanded by remember { mutableStateOf(false) }

    val voices = TtsVoices.getVoicesForProvider(provider)
    val isSaveEnabled = displayName.isNotBlank() && voiceName.isNotBlank() && apiKey.isNotBlank()

    EnclaveModalBottomSheet(onDismissRequest = onDismiss) {
        EnclaveSheetHeader(
            title = "Configure Text-to-Speech",
            subtitle = "Set up a TTS voice for your assistant.",
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
                                voiceName = ""
                                providerExpanded = false
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
                label = { Text("Display Name (e.g. My xAI TTS)") },
                modifier = Modifier.fillMaxWidth(),
            )

            ExposedDropdownMenuBox(
                expanded = voiceExpanded,
                onExpandedChange = { voiceExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                EnclaveOutlinedTextField(
                    value = voices.find { it.id == voiceName }?.displayName ?: "Select a voice",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Voice") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
                    modifier =
                        Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = voiceExpanded,
                    onDismissRequest = { voiceExpanded = false },
                ) {
                    voices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice.displayName) },
                            onClick = {
                                voiceName = voice.id
                                voiceExpanded = false
                            },
                        )
                    }
                }
            }

            EnclavePrimaryButton(
                text = "Save TTS Provider",
                onClick = {
                    onSave(
                        TtsProviderDraft(
                            id = TtsProviderId(""),
                            displayName = displayName,
                            provider = provider,
                            voiceName = voiceName,
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
