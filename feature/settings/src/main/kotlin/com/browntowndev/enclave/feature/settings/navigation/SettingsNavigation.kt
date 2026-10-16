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
        val onNavigate: (String) -> Unit = { route -> navController.navigate(route) }
        val onPopBackStack: () -> Unit = { navController.popBackStack() }
        val graphBackStackEntry: (androidx.navigation.NavBackStackEntry) -> androidx.navigation.NavBackStackEntry =
            { navController.getBackStackEntry(SettingsDestination.GRAPH) }
        settingsMainRoute(onNavigate, onPopBackStack, graphBackStackEntry, onNavigateToProfileBuilder)
        settingsByokConfigureRoute(onNavigate, onPopBackStack, graphBackStackEntry)
        settingsTtsConfigureRoute(onPopBackStack, graphBackStackEntry)
        settingsLiveVoiceConfigureRoute(onPopBackStack, graphBackStackEntry)
        settingsMediaConfigureRoute(onPopBackStack, graphBackStackEntry)
        settingsByokCustomHeadersRoute(onPopBackStack, graphBackStackEntry)
        settingsLocalModelConfigureRoute(onPopBackStack, graphBackStackEntry)
        settingsModelConfigureRoute(onPopBackStack, graphBackStackEntry)
    }
}
