package com.browntowndev.enclave.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.browntowndev.enclave.feature.settings.navigation.settingsGraph

fun NavGraphBuilder.settingsNavGraph(navController: NavHostController) {
    settingsGraph(
        navController = navController,
        onNavigateToProfileBuilder = { navController.navigate(Routes.PROFILE_BUILDER_GRAPH) },
    )
}
