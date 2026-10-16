package com.browntowndev.enclave.feature.settings.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavBackStackEntry
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
import com.browntowndev.enclave.feature.settings.ProfileBuilderRoute
import com.browntowndev.enclave.feature.settings.SettingsRoute
import com.browntowndev.enclave.feature.settings.LiveVoiceConfigureRoute
import com.browntowndev.enclave.feature.settings.TtsConfigureRoute

internal fun NavGraphBuilder.settingsMainRoute(
    onNavigate: (String) -> Unit,
    onPopBackStack: () -> Unit,
    graphBackStackEntry: (NavBackStackEntry) -> NavBackStackEntry,
    onNavigateToProfileBuilder: () -> Unit = {},
) {
    composable(
        route = SettingsDestination.MAIN,
        enterTransition = { settingsEnterTransition() },
        exitTransition = { settingsExitTransition() },
        popEnterTransition = { settingsPopEnterTransition() },
        popExitTransition = { settingsPopExitTransition() },
    ) { backStackEntry ->
        SettingsRoute(
            onCloseClick = { onPopBackStack() },
            onNavigateToProfileBuilder = onNavigateToProfileBuilder,
            onNavigateToByokConfigure = {
                onNavigate(SettingsDestination.BYOK_CONFIGURE)
            },
            onNavigateToTtsConfigure = {
                onNavigate(SettingsDestination.TTS_CONFIGURE)
            },
            onNavigateToLiveVoiceConfigure = {
                onNavigate(SettingsDestination.LIVE_VOICE_CONFIGURE)
            },
            onNavigateToLocalModelConfigure = {
                onNavigate(SettingsDestination.LOCAL_MODEL_CONFIGURE)
            },
            onNavigateToModelConfigure = { modelType ->
                onNavigate(
                    SettingsDestination.MODEL_CONFIGURE.replace("{modelType}", modelType.name),
                )
            },
            onNavigateToMediaConfigure = {
                onNavigate(SettingsDestination.MEDIA_CONFIGURE)
            },
            viewModel = settingsGraphViewModel(backStackEntry, graphBackStackEntry),
        )
    }
}

internal fun NavGraphBuilder.settingsByokConfigureRoute(
    onNavigate: (String) -> Unit,
    onPopBackStack: () -> Unit,
    graphBackStackEntry: (NavBackStackEntry) -> NavBackStackEntry,
) {
    composable(
        route = SettingsDestination.BYOK_CONFIGURE,
        enterTransition = { settingsEnterTransition() },
        exitTransition = { settingsExitTransition() },
        popEnterTransition = { settingsPopEnterTransition() },
        popExitTransition = { settingsPopExitTransition() },
    ) { backStackEntry ->
        ByokConfigureRoute(
            onNavigateBack = { onPopBackStack() },
            onNavigateToCustomHeaders = {
                onNavigate(SettingsDestination.BYOK_CUSTOM_HEADERS)
            },
            viewModel = settingsGraphViewModel(backStackEntry, graphBackStackEntry),
        )
    }
}

internal fun NavGraphBuilder.settingsTtsConfigureRoute(
    onNavigate: (String) -> Unit,
    onPopBackStack: () -> Unit,
    graphBackStackEntry: (NavBackStackEntry) -> NavBackStackEntry,
) {
    composable(
        route = SettingsDestination.TTS_CONFIGURE,
        enterTransition = { settingsEnterTransition() },
        exitTransition = { settingsExitTransition() },
        popEnterTransition = { settingsPopEnterTransition() },
        popExitTransition = { settingsPopExitTransition() },
    ) { backStackEntry ->
        TtsConfigureRoute(
            onNavigateBack = { onPopBackStack() },
            viewModel = settingsGraphViewModel(backStackEntry, graphBackStackEntry),
        )
    }
}

internal fun NavGraphBuilder.settingsLiveVoiceConfigureRoute(
    onNavigate: (String) -> Unit,
    onPopBackStack: () -> Unit,
    graphBackStackEntry: (NavBackStackEntry) -> NavBackStackEntry,
) {
    composable(
        route = SettingsDestination.LIVE_VOICE_CONFIGURE,
        enterTransition = { settingsEnterTransition() },
        exitTransition = { settingsExitTransition() },
        popEnterTransition = { settingsPopEnterTransition() },
        popExitTransition = { settingsPopExitTransition() },
    ) { backStackEntry ->
        LiveVoiceConfigureRoute(
            onNavigateBack = { onPopBackStack() },
            viewModel = settingsGraphViewModel(backStackEntry, graphBackStackEntry),
        )
    }
}

internal fun NavGraphBuilder.settingsMediaConfigureRoute(
    onNavigate: (String) -> Unit,
    onPopBackStack: () -> Unit,
    graphBackStackEntry: (NavBackStackEntry) -> NavBackStackEntry,
) {
    composable(
        route = SettingsDestination.MEDIA_CONFIGURE,
        enterTransition = { settingsEnterTransition() },
        exitTransition = { settingsExitTransition() },
        popEnterTransition = { settingsPopEnterTransition() },
        popExitTransition = { settingsPopExitTransition() },
    ) { backStackEntry ->
        MediaConfigureRoute(
            onNavigateBack = { onPopBackStack() },
            viewModel = settingsGraphViewModel(backStackEntry, graphBackStackEntry),
        )
    }
}

internal fun NavGraphBuilder.settingsByokCustomHeadersRoute(
    onNavigate: (String) -> Unit,
    onPopBackStack: () -> Unit,
    graphBackStackEntry: (NavBackStackEntry) -> NavBackStackEntry,
) {
    composable(
        route = SettingsDestination.BYOK_CUSTOM_HEADERS,
        enterTransition = { settingsEnterTransition() },
        exitTransition = { settingsExitTransition() },
        popEnterTransition = { settingsPopEnterTransition() },
        popExitTransition = { settingsPopExitTransition() },
    ) { backStackEntry ->
        ByokCustomHeadersRoute(
            onNavigateBack = { onPopBackStack() },
            viewModel = settingsGraphViewModel(backStackEntry, graphBackStackEntry),
        )
    }
}

internal fun NavGraphBuilder.settingsLocalModelConfigureRoute(
    onNavigate: (String) -> Unit,
    onPopBackStack: () -> Unit,
    graphBackStackEntry: (NavBackStackEntry) -> NavBackStackEntry,
) {
    composable(
        route = SettingsDestination.LOCAL_MODEL_CONFIGURE,
        enterTransition = { settingsEnterTransition() },
        exitTransition = { settingsExitTransition() },
        popEnterTransition = { settingsPopEnterTransition() },
        popExitTransition = { settingsPopExitTransition() },
    ) { backStackEntry ->
        LocalModelConfigureRoute(
            onNavigateBack = { onPopBackStack() },
            viewModel = settingsGraphViewModel(backStackEntry, graphBackStackEntry),
        )
    }
}

internal fun NavGraphBuilder.settingsModelConfigureRoute(
    onNavigate: (String) -> Unit,
    onPopBackStack: () -> Unit,
    graphBackStackEntry: (NavBackStackEntry) -> NavBackStackEntry,
) {
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
            onNavigateBack = { onPopBackStack() },
            viewModel = settingsGraphViewModel(backStackEntry, graphBackStackEntry),
        )
    }
}

internal fun NavGraphBuilder.profileBuilderMainRoute(onBack: () -> Unit) {
    composable(
        route = "profile_builder_main",
        enterTransition = { settingsEnterTransition() },
        exitTransition = { settingsExitTransition() },
        popEnterTransition = { settingsPopEnterTransition() },
        popExitTransition = { settingsPopExitTransition() },
    ) {
        ProfileBuilderRoute(onBack = onBack)
    }
}

@Composable
private inline fun <reified VM : ViewModel> settingsGraphViewModel(
    backStackEntry: NavBackStackEntry,
    crossinline graphBackStackEntry: (NavBackStackEntry) -> NavBackStackEntry,
): VM {
    val parentEntry = remember(backStackEntry) { graphBackStackEntry(backStackEntry) }
    return hiltViewModel(parentEntry)
}
