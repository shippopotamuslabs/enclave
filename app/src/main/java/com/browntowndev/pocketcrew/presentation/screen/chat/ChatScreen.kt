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
import com.browntowndev.pocketcrew.presentation.screen.chat.ResponseState
import com.browntowndev.pocketcrew.presentation.screen.chat.components.InputBar
import com.browntowndev.pocketcrew.presentation.screen.chat.components.MessageList
import com.browntowndev.pocketcrew.presentation.screen.chat.components.ShieldOverlay
import com.browntowndev.pocketcrew.presentation.screen.chat.components.UseTheCrewPopup
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
    onUseTheCrew: () -> Unit,
    onDismissUseTheCrew: () -> Unit,
) {
    Scaffold(
        topBar = {
            ChatTopBar(
                onMenuClick = onNavigateToHistory,
                onNewChatClick = onNewChat,
                isThinking = uiState.responseState != ResponseState.NONE,
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
                    responseState = uiState.responseState,
                    thinkingSteps = uiState.thinkingSteps,
                    thinkingStartTime = uiState.thinkingStartTime,
                    thinkingModelDisplayName = uiState.thinkingModelDisplayName,
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

            // "Use the Crew" popup - appears after Fast response
            UseTheCrewPopup(
                visible = uiState.showUseTheCrewPopup,
                onUseTheCrew = onUseTheCrew,
                onDismiss = onDismissUseTheCrew,
            )

            // InputBar — pinned above keyboard
            InputBar(
                modifier = Modifier.fillMaxWidth(),
                inputText = uiState.inputText,
                selectedMode = uiState.selectedMode,
                isExpanded = uiState.isInputExpanded,
                isThinking = uiState.responseState != ResponseState.NONE,
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
            onUseTheCrew = {},
            onDismissUseTheCrew = {},
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
                responseState = ResponseState.NONE,
            ),
            onNavigateToHistory = {},
            onNewChat = {},
            onSendMessage = {},
            onModeChange = {},
            onInputChange = {},
            onExpandToggle = {},
            onAttach = {},
            onShieldTap = {},
            onUseTheCrew = {},
            onDismissUseTheCrew = {},
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
                responseState = ResponseState.THINKING,
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
            onUseTheCrew = {},
            onDismissUseTheCrew = {},
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
            onUseTheCrew = {},
            onDismissUseTheCrew = {},
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
            onUseTheCrew = {},
            onDismissUseTheCrew = {},
        )
    }
}
