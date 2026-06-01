package com.browntowndev.enclave.feature.settings.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSectionHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoSecondaryButton

@Composable
internal fun FileImportStep(
    selectedFileNames: List<String>,
    errorMessage: String?,
    onPickFiles: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    canProceed: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        NeoSectionHeader(text = "Import conversation exports")
        Text(
            "Select your exported .zip file(s) from OpenAI, Anthropic, Google, or xAI. " +
                "Data stays on your device — only API calls leave it.",
        )
        Spacer(Modifier.height(12.dp))

        NeoSecondaryButton(
            text = if (selectedFileNames.isEmpty()) "Pick zip file(s)" else "Change files",
            onClick = onPickFiles,
            modifier = Modifier.fillMaxWidth(),
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
            )
        }

        if (selectedFileNames.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Selected files:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(selectedFileNames) { name ->
                    Text(
                        text = "• ${name.substringAfterLast('/').substringAfterLast(':')}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))
        Row {
            NeoSecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
            Spacer(Modifier.padding(4.dp))
            NeoPrimaryButton(
                text = "Continue",
                onClick = onNext,
                enabled = canProceed,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
