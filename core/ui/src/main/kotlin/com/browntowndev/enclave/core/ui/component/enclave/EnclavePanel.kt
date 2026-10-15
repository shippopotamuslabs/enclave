package com.browntowndev.enclave.core.ui.component.enclave

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

/** Bordered neo-brutalist surface for grouped settings content. */
@Composable
fun EnclavePanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .enclaveSurface(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    borderColor = enclaveBorderColor(),
                    withShadow = true,
                    shadowColor = enclaveShadowColor(),
                    shadowOffset = enclaveButtonShadowOffset(),
                ),
        content = content,
    )
}

/** Stacks standalone neo rows/cards with consistent vertical spacing between sections. */
@Composable
fun EnclaveSettingsSection(
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
fun EnclaveGroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp,
    )
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun EnclaveSettingsSectionPreview() {
    EnclaveTheme {
        EnclaveSettingsSection {
            EnclaveGroupDivider()
        }
    }
}
