package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.enclave.EnclavePrimaryButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSecondaryButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSelectionCard
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSheetHeader
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ModelSource

@Composable
fun ReassignmentView(
    modelTypes: List<ModelTypeUi>,
    reassignmentOptions: List<ReassignmentOptionUi>,
    onConfirm: (LocalModelConfigurationId?, ApiModelConfigurationId?, MediaProviderId?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedOption by remember { mutableStateOf<ReassignmentOptionUi?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        EnclaveSheetHeader(
            title = "Reassignment Required",
            onDismiss = onDismiss,
        )

        val typesList = modelTypes.joinToString { it.displayLabel }
        Text(
            text =
                if (reassignmentOptions.isEmpty()) {
                    "The following slot(s) are using this model as default: $typesList. " +
                        "No compatible models exist."
                } else {
                    "The following slot(s) are using this model as default: $typesList. " +
                        "Please select a replacement config first."
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (reassignmentOptions.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing),
            ) {
                items(reassignmentOptions) { option ->
                    EnclaveSelectionCard(
                        title = option.displayName,
                        isSelected = selectedOption == option,
                        onClick = { selectedOption = option },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        EnclavePrimaryButton(
            text = "Delete & Reassign",
            enabled = selectedOption != null,
            onClick = {
                selectedOption?.let { opt ->
                    onConfirm(
                        if (opt.source == ModelSource.ON_DEVICE) opt.configId as? LocalModelConfigurationId else null,
                        if (opt.source == ModelSource.API && opt.mediaProviderId == null) {
                            opt.configId as? ApiModelConfigurationId
                        } else {
                            null
                        },
                        opt.mediaProviderId,
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        EnclaveSecondaryButton(
            text = "Cancel",
            onClick = onDismiss,
        )
    }
}

@Composable
fun DeleteConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            EnclaveTextButton(text = confirmLabel, onClick = onConfirm)
        },
        dismissButton = {
            EnclaveTextButton(text = "Cancel", onClick = onDismiss)
        },
    )
}
