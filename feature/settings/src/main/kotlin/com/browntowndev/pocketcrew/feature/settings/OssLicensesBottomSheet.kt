package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OssLicensesBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    JumpFreeModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Open Source Licenses",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = "Pocket Crew is built with the following open-source libraries.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            LazyColumn(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                items(
                    items = OssLicenses.entries,
                    key = { it.name },
                ) { entry ->
                    OssLicenseCard(entry = entry)
                }
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

    Card(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "v${entry.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                LicenseChip(licenseType = entry.licenseType)
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
}

@Composable
private fun LicenseChip(
    licenseType: String,
    modifier: Modifier = Modifier,
) {
    SuggestionChip(
        onClick = {},
        label = {
            Text(
                text = licenseType,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        modifier = modifier,
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Preview(showBackground = true, name = "OSS Licenses Sheet — Light")
@Composable
fun PreviewOssLicensesBottomSheetLight() {
    PocketCrewTheme(darkTheme = false) {
        OssLicensesBottomSheet(onDismiss = {})
    }
}

@Preview(showBackground = true, name = "OSS Licenses Sheet — Dark")
@Composable
fun PreviewOssLicensesBottomSheetDark() {
    PocketCrewTheme(darkTheme = true) {
        OssLicensesBottomSheet(onDismiss = {})
    }
}
