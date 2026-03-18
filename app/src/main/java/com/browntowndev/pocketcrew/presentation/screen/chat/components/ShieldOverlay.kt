package com.browntowndev.pocketcrew.presentation.screen.chat.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

@Composable
fun ShieldOverlay(
    reason: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onTap,
        modifier = modifier.semantics {
            contentDescription = "Shield active: $reason"
        }
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Procedural harm blocked",
            tint = Color.Red,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Preview
@Composable
fun PreviewShieldOverlay() {
    PocketCrewTheme {
        ShieldOverlay(
            reason = "Potential harm detected",
            onTap = {}
        )
    }
}
