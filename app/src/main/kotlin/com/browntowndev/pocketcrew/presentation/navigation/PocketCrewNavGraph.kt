package com.browntowndev.pocketcrew.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.feature.chat.ChatRoute
import com.browntowndev.pocketcrew.feature.download.ModelDownloadScreen
import com.browntowndev.pocketcrew.feature.history.HistoryRoute
import com.browntowndev.pocketcrew.feature.settings.navigation.settingsGraph

private const val ANIMATION_DURATION = 300

@Suppress("FunctionNaming")
@Composable
fun PocketCrewNavGraph(
    navController: NavHostController,
    onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
    startDestination: String,
    modelsResult: DownloadModelsResult?,
    errorMessage: String? = null,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(
            route = Routes.MODEL_DOWNLOAD,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(ANIMATION_DURATION),
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(ANIMATION_DURATION),
                )
            },
        ) {
            ModelDownloadScreen(
                modelsResult = modelsResult,
                errorMessage = errorMessage,
                onReady = {
                    navController.navigate(Routes.CHAT) {
                        popUpTo(Routes.MODEL_DOWNLOAD) { inclusive = true }
                    }
                },
            )
        }
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
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(ANIMATION_DURATION),
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(ANIMATION_DURATION),
                )
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(ANIMATION_DURATION),
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(ANIMATION_DURATION),
                )
            },
        ) {
            ChatRoute(
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNewChat = {
                    navController.navigate(Routes.CHAT) {
                        popUpTo(Routes.CHAT_WITH_ID) { inclusive = true }
                    }
                },
                onShowSnackbar = onShowSnackbar,
            )
        }
        composable(
            route = Routes.HISTORY,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(ANIMATION_DURATION),
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(ANIMATION_DURATION),
                )
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(ANIMATION_DURATION),
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(ANIMATION_DURATION),
                )
            },
        ) {
            HistoryRoute(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { id ->
                    if (id != null) {
                        navController.navigate(Routes.CHAT_WITH_ID.replace("{chatId}", id.value))
                    } else {
                        navController.navigate(Routes.CHAT)
                    }
                },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS_GRAPH) },
                onShowSnackbar = onShowSnackbar,
            )
        }

        settingsGraph(
            navController = navController,
            onShowSnackbar = onShowSnackbar,
        )
    }
}
