package com.browntowndev.enclave.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.feature.settings.ByokConfigureRoute
import com.browntowndev.enclave.feature.settings.ByokCustomHeadersRoute
import com.browntowndev.enclave.feature.settings.LocalModelConfigureRoute
import com.browntowndev.enclave.feature.settings.MediaConfigureRoute
import com.browntowndev.enclave.feature.settings.ModelConfigurationRoute
import com.browntowndev.enclave.feature.settings.SettingsRoute
import com.browntowndev.enclave.feature.settings.TtsConfigureRoute

internal fun NavGraphBuilder.settingsMainRoute(navController: NavController) {
    composable(
        route = SettingsDestination.MAIN,
        enterTransition = { settingsEnterTransition() },
        exitTransition = { settingsExitTransition() },
        popEnterTransition = { settingsPopEnterTransition() },
        popExitTransition = { settingsPopExitTransition() },
    ) { backStackEntry ->
        SettingsRoute(
            onCloseClick = { navController.popBackStack() },
            onNavigateToByokConfigure = {
                navController.navigate(SettingsDestination.BYOK_CONFIGURE)
            },
            onNavigateToTtsConfigure = {
                navController.navigate(SettingsDestination.TTS_CONFIGURE)
            },
            onNavigateToLocalModelConfigure = {
                navController.navigate(SettingsDestination.LOCAL_MODEL_CONFIGURE)
            },
            onNavigateToModelConfigure = { modelType ->
                navController.navigate(
                    SettingsDestination.MODEL_CONFIGURE.replace(
                        "{modelType}",
                        modelType.name,
                    ),
                )
            },
            onNavigateToMediaConfigure = {
                navController.navigate(SettingsDestination.MEDIA_CONFIGURE)
            },
            viewModel = settingsGraphViewModel(backStackEntry, navController),
        )
    }
}

internal fun NavGraphBuilder.settingsByokConfigureRoute(navController: NavController) {
    composable(
        route = SettingsDestination.BYOK_CONFIGURE,
        enterTransition = { settingsEnterTransition() },
        exitTransition = { settingsExitTransition() },
        popEnterTransition = { settingsPopEnterTransition() },
        popExitTransition = { settingsPopExitTransition() },
    ) { backStackEntry ->
        ByokConfigureRoute(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToCustomHeaders = {
                navController.navigate(SettingsDestination.BYOK_CUSTOM_HEADERS)
            },
            viewModel = settingsGraphViewModel(backStackEntry, navController),
        )
    }
}

internal fun NavGraphBuilder.settingsTtsConfigureRoute(navController: NavController) {
    composable(
        route = SettingsDestination.TTS_CONFIGURE,
        enterTransition = { settingsEnterTransition() },
        exitTransition = { settingsExitTransition() },
        popEnterTransition = { settingsPopEnterTransition() },
        popExitTransition = { settingsPopExitTransition() },
    ) { backStackEntry ->
        TtsConfigureRoute(
            onNavigateBack = { navController.popBackStack() },
            viewModel = settingsGraphViewModel(backStackEntry, navController),
        )
    }
}

internal fun NavGraphBuilder.settingsMediaConfigureRoute(navController: NavController) {
    composable(
        route = SettingsDestination.MEDIA_CONFIGURE,
        enterTransition = { settingsEnterTransition() },
        exitTransition = { settingsExitTransition() },
        popEnterTransition = { settingsPopEnterTransition() },
        popExitTransition = { settingsPopExitTransition() },
    ) { backStackEntry ->
        MediaConfigureRoute(
            onNavigateBack = { navController.popBackStack() },
            viewModel = settingsGraphViewModel(backStackEntry, navController),
        )
    }
}

internal fun NavGraphBuilder.settingsByokCustomHeadersRoute(navController: NavController) {
    composable(
        route = SettingsDestination.BYOK_CUSTOM_HEADERS,
        enterTransition = { settingsEnterTransition() },
        exitTransition = { settingsExitTransition() },
        popEnterTransition = { settingsPopEnterTransition() },
        popExitTransition = { settingsPopExitTransition() },
    ) { backStackEntry ->
        ByokCustomHeadersRoute(
            onNavigateBack = { navController.popBackStack() },
            viewModel = settingsGraphViewModel(backStackEntry, navController),
        )
    }
}

internal fun NavGraphBuilder.settingsLocalModelConfigureRoute(navController: NavController) {
    composable(
        route = SettingsDestination.LOCAL_MODEL_CONFIGURE,
        enterTransition = { settingsEnterTransition() },
        exitTransition = { settingsExitTransition() },
        popEnterTransition = { settingsPopEnterTransition() },
        popExitTransition = { settingsPopExitTransition() },
    ) { backStackEntry ->
        LocalModelConfigureRoute(
            onNavigateBack = { navController.popBackStack() },
            viewModel = settingsGraphViewModel(backStackEntry, navController),
        )
    }
}

internal fun NavGraphBuilder.settingsModelConfigureRoute(navController: NavController) {
    composable(
        route = SettingsDestination.MODEL_CONFIGURE,
        arguments =
            listOf(
                navArgument("modelType") {
                    type = NavType.StringType
                },
            ),
        enterTransition = { settingsEnterTransition() },
        exitTransition = { settingsExitTransition() },
        popEnterTransition = { settingsPopEnterTransition() },
        popExitTransition = { settingsPopExitTransition() },
    ) { backStackEntry ->
        val modelTypeStr = backStackEntry.arguments?.getString("modelType") ?: return@composable
        if (ModelType.entries.none { it.name == modelTypeStr }) return@composable

        ModelConfigurationRoute(
            onNavigateBack = { navController.popBackStack() },
            viewModel = settingsGraphViewModel(backStackEntry, navController),
        )
    }
}

@Composable
private inline fun <reified VM : ViewModel> settingsGraphViewModel(
    backStackEntry: NavBackStackEntry,
    navController: NavController,
): VM {
    val parentEntry =
        remember(backStackEntry) {
            navController.getBackStackEntry(SettingsDestination.GRAPH)
        }
    return hiltViewModel(parentEntry)
}
