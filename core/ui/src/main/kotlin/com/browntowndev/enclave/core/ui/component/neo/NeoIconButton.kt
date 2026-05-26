package com.browntowndev.enclave.core.ui.component.neo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

@Composable
fun NeoIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = EnclaveNeoTokens.iconButtonSize,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = neoBorderColor(),
    withShadow: Boolean = true,
) {
    NeoIconButtonSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        size = size,
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        withShadow = withShadow,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
        )
    }
}

@Composable
fun NeoIconButton(
    onClick: () -> Unit,
    icon: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = EnclaveNeoTokens.iconButtonSize,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = neoBorderColor(),
    withShadow: Boolean = true,
) {
    NeoIconButtonSurface(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        size = size,
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        withShadow = withShadow,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = iconTint,
        )
    }
}

@Composable
private fun NeoIconButtonSurface(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    size: Dp,
    backgroundColor: Color,
    borderColor: Color,
    withShadow: Boolean,
    icon: @Composable () -> Unit,
) {
    val shadowColor = neoShadowColor()
    val shadowOffset = neoButtonShadowOffset()
    val interactionSource = remember { MutableInteractionSource() }
    val showShadow = withShadow && enabled

    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .then(
                        if (showShadow) {
                            Modifier.padding(end = shadowOffset, bottom = shadowOffset)
                        } else {
                            Modifier
                        },
                    ).size(size)
                    .semantics { role = Role.Button }
                    .neoSurface(
                        backgroundColor = backgroundColor,
                        borderColor = borderColor,
                        withShadow = showShadow,
                        shadowColor = shadowColor,
                        shadowOffset = shadowOffset,
                    ).clickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun NeoIconButtonPreview() {
    EnclaveTheme {
        NeoIconButton(
            onClick = {},
            icon = Icons.Default.Close,
            contentDescription = "Close",
        )
    }
}
