package com.browntowndev.enclave.core.ui.component.neo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.VisualTransformation.Companion.None
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens

/**
 * Neo-brutalist outlined field: Material outlined label-on-border with a single neo border.
 * [supportingText] is rendered below the field, not inside it.
 */
@Suppress("LongParameterList")
@Composable
fun NeoOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    colors: TextFieldColors = neoOutlinedTextFieldColors(),
) {
    val field: @Composable () -> Unit = {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            readOnly = readOnly,
            label = label,
            placeholder = placeholder,
            supportingText = null,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            shape = EnclaveNeoTokens.shape,
            textStyle = MaterialTheme.typography.bodyMedium,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            colors = colors,
        )
    }
    if (supportingText == null) {
        // No wrapper: layout modifiers from the caller (e.g. RowScope.weight) must apply
        // to this composable's root node, or two fields sharing a Row cannot split width.
        field()
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            field()
            ProvideTextStyle(
                value =
                    MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp, end = 16.dp),
                ) {
                    supportingText()
                }
            }
        }
    }
}

@Composable
fun neoOutlinedTextFieldColors(
    borderColor: Color = neoBorderColor(),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = backgroundColor,
        unfocusedContainerColor = backgroundColor,
        disabledContainerColor = backgroundColor,
        errorContainerColor = backgroundColor,
        focusedBorderColor = borderColor,
        unfocusedBorderColor = borderColor,
        disabledBorderColor = borderColor,
        errorBorderColor = borderColor,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        disabledTextColor = MaterialTheme.colorScheme.onSurface,
        focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
