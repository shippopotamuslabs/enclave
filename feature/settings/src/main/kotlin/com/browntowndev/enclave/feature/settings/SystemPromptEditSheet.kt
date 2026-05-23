package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoOutlinedTextField
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSecondaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.domain.model.inference.SystemPromptTemplates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemPromptEditSheet(
    visible: Boolean,
    prompt: String,
    onPromptChange: (String) -> Unit,
    onDismiss: () -> Unit,
    sheetState: androidx.compose.material3.SheetState,
) {
    if (!visible) return

    NeoModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        SystemPromptEditSheetContent(
            prompt = prompt,
            onPromptChange = onPromptChange,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun SystemPromptEditSheetContent(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var templateMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
    ) {
        NeoSheetHeader(
            title = "Edit System Prompt",
            onDismiss = onDismiss,
        )

        NeoSecondaryButton(
            text = "Import Template",
            onClick = { templateMenuExpanded = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = templateMenuExpanded,
            onDismissRequest = { templateMenuExpanded = false },
        ) {
            SystemPromptTemplates.getAll().forEach { (modelType, template) ->
                DropdownMenuItem(
                    text = { Text(modelType.displayName()) },
                    onClick = {
                        onPromptChange(template)
                        templateMenuExpanded = false
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(EnclaveNeoTokens.sectionSpacing))

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            NeoOutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter system prompt...") },
                singleLine = false,
                minLines = 8,
                maxLines = 32,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }

        Spacer(modifier = Modifier.height(EnclaveNeoTokens.sectionSpacing))

        NeoPrimaryButton(
            text = "Done",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
