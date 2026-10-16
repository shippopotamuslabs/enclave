package com.browntowndev.enclave.core.ui.component.enclave

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

/**
 * Neo-brutalist filter/toggle chip (replaces Material [androidx.compose.material3.FilterChip]).
 */
@Composable
fun EnclaveFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showClearIconWhenSelected: Boolean = false,
) {
    val borderColor = enclaveBorderColor()
    val backgroundColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Row(
        modifier =
            modifier
                .enclaveSurface(
                    backgroundColor = backgroundColor,
                    borderColor = borderColor,
                    withShadow = selected,
                    shadowColor = enclaveShadowColor(),
                    shadowOffset = enclaveButtonShadowOffset(),
                ).clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (selected && showClearIconWhenSelected) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun EnclaveFilterChipPreview() {
    EnclaveTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            EnclaveFilterChip(label = "A-Z", selected = true, onClick = {})
            EnclaveFilterChip(label = "Price", selected = false, onClick = {})
            EnclaveFilterChip(
                label = "Top K",
                selected = true,
                onClick = {},
                showClearIconWhenSelected = true,
            )
        }
    }
}
