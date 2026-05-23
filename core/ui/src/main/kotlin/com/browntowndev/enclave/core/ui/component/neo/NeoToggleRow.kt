package com.browntowndev.enclave.core.ui.component.neo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

@Composable
fun NeoToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    val borderColor = neoBorderColor()
    val backgroundColor = MaterialTheme.colorScheme.surface

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .neoSurface(
                    backgroundColor = backgroundColor,
                    borderColor = borderColor,
                    withShadow = false,
                )
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) {
            NeoIconTile(icon = icon, contentDescription = null)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NeoToggleRowPreview() {
    EnclaveTheme {
        NeoToggleRow(
            title = "Haptic Response",
            checked = true,
            onCheckedChange = {},
            icon = Icons.Default.Vibration,
        )
    }
}
