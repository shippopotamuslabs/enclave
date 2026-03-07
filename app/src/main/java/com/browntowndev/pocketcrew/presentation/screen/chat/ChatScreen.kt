package com.browntowndev.pocketcrew.presentation.screen.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.presentation.screen.chat.components.InputBar
import com.browntowndev.pocketcrew.presentation.screen.chat.components.MessageList
import com.browntowndev.pocketcrew.presentation.screen.chat.components.ShieldOverlay
import com.browntowndev.pocketcrew.presentation.theme.PocketCrewTheme

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onNavigateToHistory: () -> Unit,
    onNewChat: () -> Unit,
    onSendMessage: (String) -> Unit,
    onModeChange: (Mode) -> Unit,
    onInputChange: (String) -> Unit,
    onExpandToggle: () -> Unit,
    onAttach: () -> Unit,
    onShieldTap: () -> Unit,
) {
    Scaffold(
        topBar = {
            ChatTopBar(
                onMenuClick = onNavigateToHistory,
                onNewChatClick = onNewChat,
                isThinking = uiState.isThinking,
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Message area — shrinks via weight(1f) when keyboard opens
            Box(modifier = Modifier.weight(1f)) {
                MessageList(
                    modifier = Modifier.fillMaxSize(),
                    messages = uiState.messages,
                    isThinking = uiState.isThinking,
                    thinkingSteps = uiState.thinkingSteps,
                )

                if (uiState.showShield) {
                    ShieldOverlay(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        reason = uiState.shieldReason,
                        onTap = onShieldTap,
                    )
                }
            }

            // InputBar — pinned above keyboard
            InputBar(
                modifier = Modifier.fillMaxWidth(),
                inputText = uiState.inputText,
                selectedMode = uiState.selectedMode,
                isExpanded = uiState.isInputExpanded,
                onInputChange = onInputChange,
                onModeChange = onModeChange,
                onSend = onSendMessage,
                onExpandToggle = onExpandToggle,
                onAttach = onAttach,
            )
        }
    }
}

// ==================== PREVIEWS ====================

@Preview
@Composable
private fun PreviewChatScreenLight() {
    PocketCrewTheme {
        ChatScreen(
            uiState = ChatUiState(),
            onNavigateToHistory = {},
            onNewChat = {},
            onSendMessage = {},
            onModeChange = {},
            onInputChange = {},
            onExpandToggle = {},
            onAttach = {},
            onShieldTap = {},
        )
    }
}

@Preview
@Composable
private fun PreviewChatScreenDark() {
    PocketCrewTheme(darkTheme = true) {
        PreviewChatScreenLight()
    }
}

@Preview
@Composable
private fun PreviewChatScreenDynamic() {
    PocketCrewTheme(dynamicColor = true) {
        PreviewChatScreenLight()
    }
}

@Preview
@Composable
private fun PreviewChatScreenWithMessages() {
    PocketCrewTheme {
        ChatScreen(
            uiState = ChatUiState(
                messages = fakeLongMessages,
                inputText = "Hello, how are you?",
                selectedMode = Mode.FAST,
                isInputExpanded = false,
                isThinking = false,
            ),
            onNavigateToHistory = {},
            onNewChat = {},
            onSendMessage = {},
            onModeChange = {},
            onInputChange = {},
            onExpandToggle = {},
            onAttach = {},
            onShieldTap = {},
        )
    }
}

@Preview
@Composable
private fun PreviewChatScreenThinking() {
    PocketCrewTheme {
        ChatScreen(
            uiState = ChatUiState(
                messages = fakeLongMessages.takeLast(1),
                isThinking = true,
                thinkingSteps = listOf("Analyzing query...", "Refining response..."),
            ),
            onNavigateToHistory = {},
            onNewChat = {},
            onSendMessage = {},
            onModeChange = {},
            onInputChange = {},
            onExpandToggle = {},
            onAttach = {},
            onShieldTap = {},
        )
    }
}

@Preview
@Composable
private fun PreviewChatScreenShield() {
    PocketCrewTheme {
        ChatScreen(
            uiState = ChatUiState(
                showShield = true,
                shieldReason = "Potential harm detected",
            ),
            onNavigateToHistory = {},
            onNewChat = {},
            onSendMessage = {},
            onModeChange = {},
            onInputChange = {},
            onExpandToggle = {},
            onAttach = {},
            onShieldTap = {},
        )
    }
}

@Preview
@Composable
private fun PreviewChatScreenExpandedInput() {
    PocketCrewTheme {
        ChatScreen(
            uiState = ChatUiState(
                inputText = "This is a long input text to test expanded state...",
                isInputExpanded = true,
            ),
            onNavigateToHistory = {},
            onNewChat = {},
            onSendMessage = {},
            onModeChange = {},
            onInputChange = {},
            onExpandToggle = {},
            onAttach = {},
            onShieldTap = {},
        )
    }
}
