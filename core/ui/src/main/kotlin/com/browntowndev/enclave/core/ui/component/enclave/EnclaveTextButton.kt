package com.browntowndev.enclave.core.ui.component.enclave

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.core.ui.theme.EnclaveThemeId

/**
 * Borderless text action (replaces Material [androidx.compose.material3.TextButton]).
 * Accent-colored label with press feedback only; use [EnclaveSecondaryButton] for a
 * bordered, full-width action and [EnclavePrimaryButton] for the primary CTA.
 */
@Composable
fun EnclaveTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier =
            modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) color else color.copy(alpha = 0.38f),
        )
    }
}

/**
 * Content-lambda variant for labels composed of more than plain text
 * (e.g. a progress indicator beside the label).
 */
@Composable
fun EnclaveTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun EnclaveTextButtonTonalLightPreview() {
    EnclaveTheme(darkTheme = false, themeFamily = EnclaveThemeId.Tonal) {
        EnclaveTextButton(text = "Cancel", onClick = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun EnclaveTextButtonTonalDarkPreview() {
    EnclaveTheme(darkTheme = true, themeFamily = EnclaveThemeId.Tonal) {
        EnclaveTextButton(text = "Cancel", onClick = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun EnclaveTextButtonNeoLightPreview() {
    EnclaveTheme(darkTheme = false, themeFamily = EnclaveThemeId.NeoBrutalist) {
        EnclaveTextButton(text = "Cancel", onClick = {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun EnclaveTextButtonNeoDarkPreview() {
    EnclaveTheme(darkTheme = true, themeFamily = EnclaveThemeId.NeoBrutalist) {
        EnclaveTextButton(text = "Cancel", onClick = {})
    }
}
