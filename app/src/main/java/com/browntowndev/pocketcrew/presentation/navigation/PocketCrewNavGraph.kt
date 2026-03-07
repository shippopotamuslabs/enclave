package com.browntowndev.pocketcrew.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.browntowndev.pocketcrew.domain.model.ModelFile
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.presentation.screen.chat.ChatRoute
import com.browntowndev.pocketcrew.presentation.screen.history.HistoryRoute
import com.browntowndev.pocketcrew.presentation.screen.settings.SettingsRoute
import com.browntowndev.pocketcrew.presentation.screen.download.ModelDownloadScreen

@Composable
fun PocketCrewNavGraph(
    navController: NavHostController,
    onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
    startDestination: String,
    modelsResult: DownloadModelsResult?,
    errorMessage: String? = null,
) {
    val modelsToDownload = modelsResult?.modelsToDownload ?: emptyList()
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(
            route = Routes.MODEL_DOWNLOAD,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300),
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(300),
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
                }
            )
        }
        composable(
            route = Routes.CHAT,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300),
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300),
                )
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300),
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300),
                )
            },
        ) {
            ChatRoute(
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onShowSnackbar = onShowSnackbar,
            )
        }
        composable(
            route = Routes.HISTORY,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(300),
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(300),
                )
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(300),
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(300),
                )
            },
        ) {
            HistoryRoute(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { id -> navController.navigate(Routes.CHAT) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onShowSnackbar = onShowSnackbar,
            )
        }
        composable(
            route = Routes.SETTINGS,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300),
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300),
                )
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(300),
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300),
                )
            },
        ) {
            SettingsRoute(
                onNavigateBack = { navController.popBackStack() },
                onShowSnackbar = onShowSnackbar,
                onNavigateToModelDownload = {
                    navController.navigate(Routes.MODEL_DOWNLOAD)
                }
            )
        }
    }
}
