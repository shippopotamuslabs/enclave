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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSecondaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSelectionCard
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelSource
import com.browntowndev.enclave.domain.model.inference.ModelType

@Composable
fun ReassignmentView(
    modelTypes: List<ModelType>,
    reassignmentOptions: List<ReassignmentOptionUi>,
    onConfirm: (LocalModelConfigurationId?, ApiModelConfigurationId?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedOption by remember { mutableStateOf<ReassignmentOptionUi?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        NeoSheetHeader(
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
                    NeoSelectionCard(
                        title = option.displayName,
                        isSelected = selectedOption == option,
                        onClick = { selectedOption = option },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        NeoPrimaryButton(
            text = "Delete & Reassign",
            enabled = selectedOption != null,
            onClick = {
                selectedOption?.let { opt ->
                    onConfirm(
                        if (opt.source == ModelSource.ON_DEVICE) opt.configId as? LocalModelConfigurationId else null,
                        if (opt.source == ModelSource.API) opt.configId as? ApiModelConfigurationId else null,
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        NeoSecondaryButton(
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
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
