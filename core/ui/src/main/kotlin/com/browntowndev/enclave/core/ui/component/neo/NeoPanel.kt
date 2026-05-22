package com.browntowndev.enclave.core.ui.component.neo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

/** Stacks standalone neo rows/cards with consistent vertical spacing between sections. */
@Composable
fun NeoSettingsSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing),
        content = content,
    )
}

@Composable
fun NeoPanel(
    modifier: Modifier = Modifier,
    withShadow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderColor = neoBorderColor()
    val backgroundColor = MaterialTheme.colorScheme.surface
    val shadowColor = neoShadowColor()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .neoSurface(
                    backgroundColor = backgroundColor,
                    borderColor = borderColor,
                    withShadow = withShadow,
                    shadowColor = shadowColor,
                )
                .padding(vertical = 4.dp),
        content = content,
    )
}

@Composable
fun NeoGroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp,
    )
}

@Preview(showBackground = true)
@Composable
private fun NeoPanelPreview() {
    EnclaveTheme {
        NeoPanel {
            NeoGroupDivider()
        }
    }
}
