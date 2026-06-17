package com.browntowndev.enclave.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.component.neo.neoBorderColor
import com.browntowndev.enclave.core.ui.component.neo.neoSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OssLicensesBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    NeoModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        NeoSheetHeader(
            title = "Licenses & Attributions",
            subtitle =
                "Open-source libraries, bundled AI model terms, and third-party runtime notices " +
                    "used by Enclave.",
            onDismiss = onDismiss,
        )
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing),
        ) {
            items(
                items = OssLicenses.entries,
                key = { it.name },
            ) { entry ->
                OssLicenseCard(entry = entry)
            }
        }
    }
}

@Composable
private fun OssLicenseCard(
    entry: OssLicenseEntry,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .neoSurface(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    borderColor = neoBorderColor(),
                    withShadow = false,
                ).clickable { expanded = !expanded }
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (entry.version.startsWith("BOM")) entry.version else "v${entry.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = entry.licenseType,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .neoSurface(
                            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            borderColor = neoBorderColor(),
                            withShadow = false,
                        ).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = entry.licenseText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, name = "OSS Licenses Sheet — Light")
@Composable
fun PreviewOssLicensesBottomSheetLight() {
    EnclaveTheme(darkTheme = false) {
        OssLicensesBottomSheet(onDismiss = {})
    }
}

@Preview(showBackground = true, name = "OSS Licenses Sheet — Dark")
@Composable
fun PreviewOssLicensesBottomSheetDark() {
    EnclaveTheme(darkTheme = true) {
        OssLicensesBottomSheet(onDismiss = {})
    }
}
