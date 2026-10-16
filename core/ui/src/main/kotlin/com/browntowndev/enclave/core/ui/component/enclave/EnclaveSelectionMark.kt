package com.browntowndev.enclave.core.ui.component.enclave

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

/**
 * Neo-brutalist grid selection mark (square tile, not a Material circular checkbox).
 */
@Composable
fun EnclaveSelectionMark(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor = enclaveBorderColor()
    val backgroundColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        }

    Box(
        modifier =
            modifier
                .size(28.dp)
                .semantics {
                    contentDescription = if (selected) "Selected" else "Not selected"
                }.enclaveSurface(
                    backgroundColor = backgroundColor,
                    borderColor = borderColor,
                    withShadow = selected,
                    shadowColor = enclaveShadowColor(),
                    shadowOffset = enclaveButtonShadowOffset(),
                    shape = EnclaveNeoTokens.shape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, backgroundColor = 0xFF333333)
@Composable
private fun EnclaveSelectionMarkPreview() {
    EnclaveTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EnclaveSelectionMark(selected = false)
            EnclaveSelectionMark(selected = true)
        }
    }
}
