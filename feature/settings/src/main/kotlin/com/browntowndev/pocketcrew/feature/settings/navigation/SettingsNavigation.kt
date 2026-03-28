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
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.feature.settings.ByokConfigureRoute
import com.browntowndev.pocketcrew.feature.settings.ModelConfigurationRoute
import com.browntowndev.pocketcrew.feature.settings.SettingsRoute

object SettingsDestination {
    const val GRAPH = "settings_graph"
    const val MAIN = "settings_main"
    const val BYOK_CONFIGURE = "byok_configure?apiModelId={apiModelId}"
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
            SettingsRoute(
                onNavigateBack = { navController.popBackStack() },
                onShowSnackbar = onShowSnackbar,
                onNavigateToModelDownload = {
                    navController.navigate(SettingsDestination.MODEL_DOWNLOAD)
                },
                onNavigateToByokConfigure = {
                    navController.navigate("byok_configure")
                },
                onNavigateToModelConfigure = { modelType ->
                    navController.navigate("model_configure/${modelType.name}")
                }
            )
        }

        composable(
            route = SettingsDestination.BYOK_CONFIGURE,
            arguments = listOf(
                navArgument("apiModelId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
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
            val apiModelIdStr = backStackEntry.arguments?.getString("apiModelId")
            val apiModelId = apiModelIdStr?.toLongOrNull()
            ByokConfigureRoute(
                apiModelId = apiModelId,
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
            ModelConfigurationRoute(
                modelType = modelType,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
