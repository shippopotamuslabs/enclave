package com.browntowndev.enclave.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoIconButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSearchField

@Composable
fun HistoryTopBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NeoIconButton(
            onClick = onBackClick,
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Back to chat",
        )
        NeoSearchField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = "Search Enclave History",
            modifier = Modifier.weight(1f),
        )
        NeoIconButton(
            onClick = onSettingsClick,
            icon = Icons.Default.Settings,
            contentDescription = "Open settings",
        )
    }
}
