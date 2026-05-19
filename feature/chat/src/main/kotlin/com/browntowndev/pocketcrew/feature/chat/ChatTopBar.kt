package com.browntowndev.pocketcrew.feature.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.R
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
) {
    CenterAlignedTopAppBar(
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open history",
                )
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Enclave",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
        actions = {
            IconButton(onClick = onNewChatClick) {
                Icon(
                    painter = painterResource(R.drawable.edit_square),
                    contentDescription = "Start new chat",
                    modifier =
                        Modifier
                            .size(40.dp)
                            .padding(8.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PreviewChatTopBarLight() {
    PocketCrewTheme {
        ChatTopBar({}, {})
    }
}

@Suppress("UnusedPrivateMember")
@Preview
@Composable
private fun PreviewChatTopBarDark() {
    PocketCrewTheme(darkTheme = true) {
        ChatTopBar({}, {})
    }
}
