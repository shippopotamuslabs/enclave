package com.browntowndev.enclave.core.ui.component.enclave

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

@Composable
fun EnclaveDestructiveRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isAccountDanger: Boolean = false,
) {
    val borderColor = enclaveBorderColor()
    val backgroundColor =
        if (isAccountDanger) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    val titleColor =
        if (isAccountDanger) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val icon: ImageVector = if (isAccountDanger) Icons.Default.DeleteForever else Icons.Default.Delete

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.4f)
                .enclaveSurface(
                    backgroundColor = backgroundColor,
                    borderColor = borderColor,
                    withShadow = false,
                ).clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier =
                Modifier
                    .padding(start = 16.dp)
                    .weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = titleColor,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun EnclaveDestructiveRowPreview() {
    EnclaveTheme {
        EnclaveDestructiveRow(
            title = "Delete all chats",
            subtitle = "Permanently removes all conversations and their messages.",
            onClick = {},
        )
    }
}
