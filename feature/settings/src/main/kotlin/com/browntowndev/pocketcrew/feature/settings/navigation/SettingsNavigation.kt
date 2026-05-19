package com.browntowndev.pocketcrew.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.navigation

fun NavGraphBuilder.settingsGraph(navController: NavController) {
    navigation(
        startDestination = SettingsDestination.MAIN,
        route = SettingsDestination.GRAPH,
    ) {
        settingsMainRoute(navController)
        settingsByokConfigureRoute(navController)
        settingsTtsConfigureRoute(navController)
        settingsMediaConfigureRoute(navController)
        settingsByokCustomHeadersRoute(navController)
        settingsLocalModelConfigureRoute(navController)
        settingsModelConfigureRoute(navController)
    }
}
