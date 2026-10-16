package com.browntowndev.enclave.core.ui.component.enclave

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens

/**
 * Sheet tabs using [MaterialTheme.colorScheme.surface] so they match [EnclaveModalBottomSheet].
 */
@Composable
fun EnclaveSheetTabRow(
    selectedTabIndex: Int,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.surface
    val borderColor = enclaveBorderColor()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(background),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, title ->
                val selected = index == selectedTabIndex
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable { onTabSelected(index) }
                            .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color =
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (selected) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(EnclaveNeoTokens.borderWidth)
                                    .background(MaterialTheme.colorScheme.primary),
                        )
                    } else {
                        Spacer(modifier = Modifier.height(EnclaveNeoTokens.borderWidth))
                    }
                }
            }
        }
        HorizontalDivider(thickness = EnclaveNeoTokens.borderWidth, color = borderColor)
    }
}
