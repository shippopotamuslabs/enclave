package com.browntowndev.enclave.core.ui.component.neo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * API key entry with label on the field border and encryption hints rendered below the box.
 */
@Composable
fun NeoApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyPresent: Boolean,
    modifier: Modifier = Modifier,
    onClearKey: (() -> Unit)? = null,
    hintWhenEmpty: String? = null,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val showSavedIndicator = keyPresent && value.isEmpty()
    val hintText =
        when {
            hintWhenEmpty != null && !keyPresent -> hintWhenEmpty
            keyPresent && value.isEmpty() ->
                "The stored key stays encrypted and hidden. Typing here switches back to a new key."
            keyPresent ->
                "You are entering a new key. The previously stored key will be replaced upon saving."
            else -> null
        }

    Column(modifier = modifier) {
        NeoOutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label)
                    if (showSavedIndicator) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Key saved",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                when {
                    showSavedIndicator -> Text("Saved securely")
                    keyPresent -> Text("••••••••••••••••")
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle key visibility",
                    )
                }
            },
        )
        if (hintText != null) {
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 4.dp, end = 16.dp),
            )
        }
        if (keyPresent && onClearKey != null) {
            TextButton(
                onClick = onClearKey,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Clear Saved Key", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
