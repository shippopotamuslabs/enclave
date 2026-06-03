package com.browntowndev.enclave.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.navigation
import com.browntowndev.enclave.feature.settings.navigation.profileBuilderGraph

fun NavGraphBuilder.profileBuilderNavGraph(navController: NavHostController) {
    navigation(
        startDestination = Routes.PROFILE_BUILDER_MAIN,
        route = Routes.PROFILE_BUILDER_GRAPH,
    ) {
        profileBuilderGraph(onBack = { navController.popBackStack() })
    }
}
