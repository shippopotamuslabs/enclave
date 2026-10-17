package com.browntowndev.enclave.feature.chat.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSecondaryIconButton
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

@Composable
fun ShieldOverlay(
    reason: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EnclaveSecondaryIconButton(
        onClick = onTap,
        icon = Icons.Default.Warning,
        contentDescription = "Procedural harm blocked",
        modifier =
            modifier.semantics {
                contentDescription = "Shield active: $reason"
            },
        iconTint = Color.Red,
    )
}

@Preview
@Composable
fun PreviewShieldOverlay() {
    EnclaveTheme {
        ShieldOverlay(
            reason = "Potential harm detected",
            onTap = {},
        )
    }
}
