package com.browntowndev.enclave.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OnboardingRoute(
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingEvent.NavigateToChat -> onOnboardingComplete()
            }
        }
    }

    OnboardingScreen(
        uiState = uiState,
        onAgeConfirmedChange = viewModel::onAgeConfirmedChange,
        onNextStep = viewModel::onNextStep,
        onPreviousStep = viewModel::onPreviousStep,
        onModelChoiceSelected = viewModel::onModelChoiceSelected,
        onProviderSelected = viewModel::onProviderSelected,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onDiscoverModels = viewModel::onDiscoverModels,
        onModelSelected = viewModel::onModelSelected,
        onSkipMedia = viewModel::onSkipMedia,
        onSaveTtsProvider = viewModel::onSaveTtsProvider,
        onSaveMediaProvider = viewModel::onSaveMediaProvider,
        onCompleteOnboarding = viewModel::onCompleteOnboarding,
        modifier = modifier,
    )
}
