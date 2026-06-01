package com.browntowndev.enclave.feature.settings.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSectionHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoSecondaryButton

@Composable
internal fun ReviewEditStep(
    profileContent: String,
    isSaving: Boolean,
    onContentEdited: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        NeoSectionHeader(text = "Review your profile")
        Text("Edit the profile before saving. It will be injected at the start of every new conversation.")
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = profileContent,
            onValueChange = onContentEdited,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            label = { Text("Profile") },
            minLines = 10,
        )

        Spacer(Modifier.height(16.dp))
        Row {
            NeoSecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.weight(1f))
            Spacer(Modifier.padding(4.dp))
            NeoPrimaryButton(
                text = if (isSaving) "Saving…" else "Approve & Save",
                onClick = onSave,
                enabled = !isSaving && profileContent.isNotBlank(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
