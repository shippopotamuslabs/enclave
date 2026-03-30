package com.browntowndev.pocketcrew.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.navArgument
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.feature.settings.ByokConfigureRoute
import com.browntowndev.pocketcrew.feature.settings.LocalModelConfigureRoute
import com.browntowndev.pocketcrew.feature.settings.ModelConfigurationRoute
import com.browntowndev.pocketcrew.feature.settings.SettingsRoute

object SettingsDestination {
    const val GRAPH = "settings_graph"
    const val MAIN = "settings_main"
    const val BYOK_CONFIGURE = "byok_configure"
    const val LOCAL_MODEL_CONFIGURE = "local_model_configure"
    const val MODEL_CONFIGURE = "model_configure/{modelType}"
    const val MODEL_DOWNLOAD = "model_download"
}

fun NavGraphBuilder.settingsGraph(
    navController: NavController,
    onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
) {
    navigation(
        startDestination = SettingsDestination.MAIN,
        route = SettingsDestination.GRAPH
    ) {
        composable(
            route = SettingsDestination.MAIN,
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
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(300),
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300),
                )
            },
        ) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(SettingsDestination.GRAPH)
            }
            SettingsRoute(
                onCloseClick = { navController.popBackStack() },
                onNavigateToModelDownload = {
                    navController.navigate(SettingsDestination.MODEL_DOWNLOAD)
                },
                onNavigateToByokConfigure = {
                    navController.navigate(SettingsDestination.BYOK_CONFIGURE)
                },
                onNavigateToLocalModelConfigure = {
                    navController.navigate(SettingsDestination.LOCAL_MODEL_CONFIGURE)
                },
                onNavigateToModelConfigure = { modelType ->
                    navController.navigate("model_configure/${modelType.name}")
                },
                viewModel = hiltViewModel(parentEntry)
            )
        }

        composable(
            route = SettingsDestination.BYOK_CONFIGURE,
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
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(300),
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300),
                )
            },
        ) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(SettingsDestination.GRAPH)
            }
            ByokConfigureRoute(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(route = SettingsDestination.LOCAL_MODEL_CONFIGURE) {
            LocalModelConfigureRoute(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = SettingsDestination.MODEL_CONFIGURE,
            arguments = listOf(
                navArgument("modelType") {
                    type = NavType.StringType
                }
            ),
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
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(300),
                )
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(300),
                )
            },
        ) { backStackEntry ->
            val modelTypeStr = backStackEntry.arguments?.getString("modelType")
                ?: return@composable
            val modelType = try {
                ModelType.valueOf(modelTypeStr)
            } catch (e: IllegalArgumentException) {
                return@composable
            }
            
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(SettingsDestination.GRAPH)
            }
            ModelConfigurationRoute(
                modelType = modelType,
                onNavigateBack = { navController.popBackStack() },
                viewModel = hiltViewModel(parentEntry)
            )
        }
    }
}
