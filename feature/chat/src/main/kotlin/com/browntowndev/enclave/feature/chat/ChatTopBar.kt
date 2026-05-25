package com.browntowndev.enclave.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.browntowndev.enclave.core.ui.R
import com.browntowndev.enclave.core.ui.component.neo.NeoAppBarChrome
import com.browntowndev.enclave.core.ui.component.neo.NeoIconButton

@Composable
fun ChatTopBar(
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onViewChatStatsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    NeoAppBarChrome(modifier = modifier) {
        NeoIconButton(
            onClick = onMenuClick,
            icon = Icons.Default.Menu,
            contentDescription = "Open history",
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Enclave",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box {
            NeoIconButton(
                onClick = { overflowExpanded = true },
                icon = Icons.Default.MoreVert,
                contentDescription = "Chat options",
            )
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("New chat") },
                    onClick = {
                        overflowExpanded = false
                        onNewChatClick()
                    },
                )
                DropdownMenuItem(
                    text = { Text("View chat stats") },
                    onClick = {
                        overflowExpanded = false
                        onViewChatStatsClick()
                    },
                )
            }
        }
    }
}
