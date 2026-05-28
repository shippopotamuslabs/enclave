package com.browntowndev.enclave.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.browntowndev.enclave.feature.onboarding.ModelChoice
import com.browntowndev.enclave.feature.onboarding.OnboardingRoute

fun NavGraphBuilder.onboardingNavGraph(onOnboardingComplete: (ModelChoice) -> Unit) {
    composable(Routes.ONBOARDING) {
        OnboardingRoute(onOnboardingComplete = onOnboardingComplete)
    }
}
