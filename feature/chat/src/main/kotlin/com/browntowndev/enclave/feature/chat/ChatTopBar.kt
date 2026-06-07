package com.browntowndev.enclave.feature.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.browntowndev.enclave.core.ui.component.neo.NeoAppBarChrome
import com.browntowndev.enclave.core.ui.component.neo.NeoIconButton
import com.browntowndev.enclave.core.ui.component.neo.neoBorderColor
import com.browntowndev.enclave.core.ui.component.neo.neoButtonShadowOffset
import com.browntowndev.enclave.core.ui.component.neo.neoShadowColor
import com.browntowndev.enclave.core.ui.component.neo.neoSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

@Composable
fun ChatTopBar(
    selectedMode: ChatModeUi = ChatModeUi.FAST,
    selectedModeModelName: String? = null,
    modeModelNames: Map<ChatModeUi, String?> = emptyMap(),
    onModeChange: (ChatModeUi) -> Unit = {},
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onViewChatStatsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var modeExpanded by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }

    NeoAppBarChrome(modifier = modifier) {
        NeoIconButton(
            onClick = onMenuClick,
            icon = Icons.Default.Menu,
            contentDescription = "Open history",
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable { modeExpanded = true },
            contentAlignment = Alignment.Center,
        ) {
            val chevronRotation by animateFloatAsState(
                targetValue = if (modeExpanded) 180f else 0f,
                label = "topBarModeChevron",
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = selectedMode.getDisplayName(context),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Change mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(chevronRotation),
                    )
                }
                selectedModeModelName?.takeIf { it.isNotBlank() }?.let { modelName ->
                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(
                expanded = modeExpanded,
                onDismissRequest = { modeExpanded = false },
                properties = PopupProperties(focusable = false),
                modifier =
                    Modifier
                        .width(200.dp)
                        .neoSurface(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            borderColor = neoBorderColor(),
                            withShadow = true,
                            shadowColor = neoShadowColor(),
                            shadowOffset = neoButtonShadowOffset(),
                            shape = EnclaveNeoTokens.shape,
                        ),
            ) {
                ChatModeUi.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    mode.getDisplayName(context),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                modeModelNames[mode]?.takeIf { it.isNotBlank() }?.let { modelName ->
                                    Text(
                                        text = modelName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        onClick = {
                            onModeChange(mode)
                            modeExpanded = false
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(mode.iconRes),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
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

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, name = "Chat Top Bar — collapsed")
@Composable
private fun ChatTopBarPreview() {
    EnclaveTheme {
        ChatTopBar(
            selectedMode = ChatModeUi.FAST,
            selectedModeModelName = "Gemma 4 E4B",
            modeModelNames =
                mapOf(
                    ChatModeUi.FAST to "Gemma 4 E4B",
                    ChatModeUi.THINKING to "Qwen3 4B Thinking",
                    ChatModeUi.COUNCIL to "Council",
                ),
            onMenuClick = {},
            onNewChatClick = {},
        )
    }
}
