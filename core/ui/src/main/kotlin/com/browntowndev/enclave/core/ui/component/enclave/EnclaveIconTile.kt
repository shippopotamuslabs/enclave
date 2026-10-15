package com.browntowndev.enclave.core.ui.component.enclave

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

@Composable
fun EnclaveIconTile(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val borderColor = enclaveBorderColor()
    val backgroundColor = MaterialTheme.colorScheme.primary
    val iconTint = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier =
            modifier
                .size(EnclaveNeoTokens.iconTileSize)
                .enclaveSurface(
                    backgroundColor = backgroundColor,
                    borderColor = borderColor,
                    withShadow = false,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun EnclaveIconTilePreview() {
    EnclaveTheme {
        EnclaveIconTile(icon = Icons.Default.Cloud, contentDescription = null)
    }
}
