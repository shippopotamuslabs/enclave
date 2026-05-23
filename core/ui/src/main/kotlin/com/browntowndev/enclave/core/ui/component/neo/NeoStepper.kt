package com.browntowndev.enclave.core.ui.component.neo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

@Composable
fun NeoStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = 99,
) {
    val borderColor = neoBorderColor()

    Row(
        modifier =
            modifier
                .width(128.dp)
                .height(44.dp)
                .neoSurface(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    borderColor = borderColor,
                    withShadow = false,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeoStepperSideButton(
            onClick = { onValueChange((value - 1).coerceAtLeast(min)) },
            icon = Icons.Default.Remove,
            contentDescription = "Decrease",
        )
        VerticalDivider(
            thickness = EnclaveNeoTokens.borderWidth,
            color = borderColor,
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value.toString(),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        VerticalDivider(
            thickness = EnclaveNeoTokens.borderWidth,
            color = borderColor,
        )
        NeoStepperSideButton(
            onClick = { onValueChange((value + 1).coerceAtMost(max)) },
            icon = Icons.Default.Add,
            contentDescription = "Increase",
        )
    }
}

@Composable
private fun NeoStepperSideButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .semantics {
                    role = Role.Button
                    this.contentDescription = contentDescription
                }
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun NeoStepperPreview() {
    EnclaveTheme {
        NeoStepper(value = 3, onValueChange = {})
    }
}
