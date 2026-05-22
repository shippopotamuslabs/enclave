package com.browntowndev.enclave.presentation.navigation

import androidx.navigation.NavOptionsBuilder

/**
 * Opens a chat from History by replacing the current chat destination so [chatId] nav args apply.
 *
 * Do not use [NavOptionsBuilder.launchSingleTop] here: it reuses the existing `chat` entry
 * (often the blank start destination with null [chatId]) and the tapped chat id is ignored.
 */
fun NavOptionsBuilder.openChatFromHistory() {
    popUpTo(Routes.CHAT_WITH_ID) { inclusive = true }
    launchSingleTop = false
}
