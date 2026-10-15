package com.browntowndev.enclave.feature.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveAppBarChrome
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveIconButton
import com.browntowndev.enclave.core.ui.component.enclave.enclaveBorderColor
import com.browntowndev.enclave.core.ui.component.enclave.enclaveButtonShadowOffset
import com.browntowndev.enclave.core.ui.component.enclave.enclaveShadowColor
import com.browntowndev.enclave.core.ui.component.enclave.enclaveSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import androidx.compose.ui.res.stringResource

@Composable
fun ChatTopBar(
    selectedMode: ChatModeUi = ChatModeUi.FAST,
    selectedModeModelName: String? = null,
    modeModelNames: Map<ChatModeUi, String?> = emptyMap(),
    onModeChange: (ChatModeUi) -> Unit = {},
    onOtherClick: () -> Unit = {},
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onViewChatStatsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var modeExpanded by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }

    EnclaveAppBarChrome(modifier = modifier) {
        EnclaveIconButton(
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
                        .enclaveSurface(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            borderColor = enclaveBorderColor(),
                            withShadow = true,
                            shadowColor = enclaveShadowColor(),
                            shadowOffset = enclaveButtonShadowOffset(),
                            shape = EnclaveNeoTokens.shape,
                        ),
            ) {
                ChatModeUi.entries.filter { it != ChatModeUi.OTHER }.forEach { mode ->
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
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.mode_other),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onOtherClick()
                        modeExpanded = false
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.network_intelligence),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
        Box {
            EnclaveIconButton(
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
