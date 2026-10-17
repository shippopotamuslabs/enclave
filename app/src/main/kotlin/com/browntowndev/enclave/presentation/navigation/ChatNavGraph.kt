package com.browntowndev.enclave.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.feature.chat.ChatRoute
import com.browntowndev.enclave.feature.chat.ChatViewModel
import com.browntowndev.enclave.feature.history.HistoryRoute

fun NavGraphBuilder.chatNavGraph(
    actions: ChatNavActions,
    historyActions: HistoryNavActions,
) {
    composable(
        route = Routes.CHAT_WITH_ID,
        arguments =
            listOf(
                navArgument("chatId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        deepLinks =
            listOf(
                navDeepLink {
                    uriPattern = Routes.CHAT_DEEP_LINK_PATTERN
                },
            ),
        enterTransition = { hierarchicalEnterTransition() },
        exitTransition = { hierarchicalExitTransition() },
        popEnterTransition = { hierarchicalPopEnterTransition() },
        popExitTransition = { hierarchicalPopExitTransition() },
    ) { backStackEntry ->
        val navigationChatId =
            backStackEntry.arguments?.getString("chatId")?.let { chatIdValue -> ChatId(chatIdValue) }
        ChatRoute(
            onNavigateToHistory = actions.onNavigateToHistory,
            onNewChat = actions.onNewChatFromChat,
            onNavigateToStudio = actions.onNavigateToStudio,
            onShowSnackbar = actions.onShowSnackbar,
            navigationChatId = navigationChatId?.value,
        )
    }

    composable(
        route = Routes.HISTORY,
        enterTransition = { hierarchicalEnterTransition() },
        exitTransition = { hierarchicalExitTransition() },
        popEnterTransition = { hierarchicalPopEnterTransition() },
        popExitTransition = { hierarchicalPopExitTransition() },
    ) {
        HistoryRoute(
            onNavigateBack = historyActions.onNavigateBack,
            onNavigateToChat = historyActions.onNavigateToChat,
            onNavigateToStudio = historyActions.onNavigateToStudio,
            onNavigateToGallery = historyActions.onNavigateToGallery,
            onNavigateToSettings = historyActions.onNavigateToSettings,
        )
    }

    composable(
        route = Routes.SHARED_CHAT,
        arguments =
            listOf(
                navArgument("shareId") { type = NavType.StringType },
                navArgument("key") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        deepLinks =
            listOf(
                navDeepLink {
                    uriPattern = Routes.SHARED_CHAT_DEEP_LINK_PATTERN
                },
            ),
    ) { backStackEntry ->
        SharedChatRoute(
            shareId = backStackEntry.arguments?.getString("shareId"),
            decryptionKey = backStackEntry.arguments?.getString("key"),
            actions = actions,
        )
    }
}

@Composable
private fun SharedChatRoute(
    shareId: String?,
    decryptionKey: String?,
    actions: ChatNavActions,
) {
    val chatViewModel = hiltViewModel<ChatViewModel>()

    LaunchedEffect(shareId, decryptionKey) {
        if (shareId != null) {
            chatViewModel.onLoadSharedChat(shareId, decryptionKey)
        }
    }

    ChatRoute(
        onNavigateToHistory = actions.onNavigateToHistory,
        onNewChat = actions.onNewChatFromShared,
        onNavigateToStudio = actions.onNavigateToStudio,
        onShowSnackbar = actions.onShowSnackbar,
        onNavigateToImportedChat = { chatIdValue -> actions.onNavigateToImportedChat(ChatId(chatIdValue)) },
        viewModel = chatViewModel,
    )
}
