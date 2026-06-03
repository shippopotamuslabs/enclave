package com.browntowndev.enclave.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.navigation

fun NavGraphBuilder.profileBuilderGraph(onBack: () -> Unit) {
    profileBuilderMainRoute(onBack = onBack)
}

fun NavGraphBuilder.settingsGraph(
    navController: NavController,
    onNavigateToProfileBuilder: () -> Unit = {},
) {
    navigation(
        startDestination = SettingsDestination.MAIN,
        route = SettingsDestination.GRAPH,
    ) {
        settingsMainRoute(navController, onNavigateToProfileBuilder)
        settingsByokConfigureRoute(navController)
        settingsTtsConfigureRoute(navController)
        settingsMediaConfigureRoute(navController)
        settingsByokCustomHeadersRoute(navController)
        settingsLocalModelConfigureRoute(navController)
        settingsModelConfigureRoute(navController)
    }
}
