package com.browntowndev.pocketcrew.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.browntowndev.pocketcrew.feature.settings.navigation.settingsGraph

fun NavGraphBuilder.settingsNavGraph(navController: NavHostController) {
    settingsGraph(navController = navController)
}
