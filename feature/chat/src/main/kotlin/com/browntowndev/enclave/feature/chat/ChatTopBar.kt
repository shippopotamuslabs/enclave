package com.browntowndev.enclave.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.R
import com.browntowndev.enclave.core.ui.component.neo.NeoIconButton
import com.browntowndev.enclave.core.ui.component.neo.neoBorderColor
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens

@Composable
fun ChatTopBar(
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Distinct from message list (background): matches legacy CenterAlignedTopAppBar surface chrome.
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = EnclaveNeoTokens.screenHorizontalPadding,
                            vertical = 8.dp,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                NeoIconButton(
                    onClick = onNewChatClick,
                    icon = painterResource(R.drawable.edit_square),
                    contentDescription = "Start new chat",
                )
            }
            HorizontalDivider(
                thickness = EnclaveNeoTokens.borderWidth,
                color = neoBorderColor(),
            )
        }
    }
}
