package com.browntowndev.enclave.feature.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.usecase.settings.MediaProviderDraft
import com.browntowndev.enclave.domain.usecase.settings.TtsProviderDraft
import com.browntowndev.enclave.feature.onboarding.steps.CompletionStep
import com.browntowndev.enclave.feature.onboarding.steps.MediaStep
import com.browntowndev.enclave.feature.onboarding.steps.ModelChoiceStep
import com.browntowndev.enclave.feature.onboarding.steps.WelcomeAgeGateStep

private const val ONBOARDING_STEP_COUNT = 4

@Suppress("UnusedParameter")
@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onAgeConfirmedChange: (Boolean) -> Unit,
    onNextStep: () -> Unit,
    onPreviousStep: () -> Unit,
    onModelChoiceSelected: (ModelChoice) -> Unit,
    onProviderSelected: (ApiProvider) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onDiscoverModels: () -> Unit,
    onModelSelected: (String) -> Unit,
    onSkipMedia: () -> Unit,
    onSaveTtsProvider: (TtsProviderDraft) -> Unit,
    onSaveMediaProvider: (MediaProviderDraft) -> Unit,
    onCompleteOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = uiState.currentStep,
        pageCount = { ONBOARDING_STEP_COUNT },
    )

    // Sync pager to ViewModel currentStep
    LaunchedEffect(uiState.currentStep) {
        if (pagerState.currentPage != uiState.currentStep) {
            pagerState.animateScrollToPage(uiState.currentStep)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            userScrollEnabled = false,
        ) { page ->
            when (page) {
                0 -> WelcomeAgeGateStep(
                    ageConfirmed = uiState.ageConfirmed,
                    onAgeConfirmedChange = onAgeConfirmedChange,
                    modifier = Modifier.fillMaxSize(),
                )
                1 -> ModelChoiceStep(
                    selectedModelChoice = uiState.selectedModelChoice,
                    selectedProvider = uiState.selectedProvider,
                    apiKey = uiState.apiKey,
                    discoveredModels = uiState.discoveredModels,
                    selectedModelId = uiState.selectedModelId,
                    isDiscoveringModels = uiState.isDiscoveringModels,
                    discoveryError = uiState.discoveryError,
                    onModelChoiceSelected = onModelChoiceSelected,
                    onProviderSelected = onProviderSelected,
                    onApiKeyChanged = onApiKeyChanged,
                    onDiscoverModels = onDiscoverModels,
                    onModelSelected = onModelSelected,
                    modifier = Modifier.fillMaxSize(),
                )
                2 -> MediaStep(
                    isTtsConfigured = uiState.isTtsConfigured,
                    isMediaConfigured = uiState.isMediaConfigured,
                    onSaveTtsProvider = onSaveTtsProvider,
                    onSaveMediaProvider = onSaveMediaProvider,
                    onSkipMedia = onSkipMedia,
                    modifier = Modifier.fillMaxSize(),
                )
                3 -> CompletionStep(
                    isSaving = uiState.isSaving,
                    saveError = uiState.saveError,
                    onCompleteOnboarding = onCompleteOnboarding,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Preview(name = "OnboardingScreen — Light", showBackground = true)
@Composable
internal fun OnboardingScreenLightPreview() {
    EnclaveTheme(darkTheme = false) {
        OnboardingScreen(
            uiState = OnboardingUiState(currentStep = 0),
            onAgeConfirmedChange = {},
            onNextStep = {},
            onPreviousStep = {},
            onModelChoiceSelected = {},
            onProviderSelected = {},
            onApiKeyChanged = {},
            onDiscoverModels = {},
            onModelSelected = {},
            onSkipMedia = {},
            onSaveTtsProvider = {},
            onSaveMediaProvider = {},
            onCompleteOnboarding = {},
        )
    }
}

@Preview(name = "OnboardingScreen — Dark", showBackground = true)
@Composable
internal fun OnboardingScreenDarkPreview() {
    EnclaveTheme(darkTheme = true) {
        OnboardingScreen(
            uiState = OnboardingUiState(
                currentStep = 1,
                selectedModelChoice = ModelChoice.LOCAL,
                discoveredModels = listOf(DiscoveredApiModel(id = "model-1")),
            ),
            onAgeConfirmedChange = {},
            onNextStep = {},
            onPreviousStep = {},
            onModelChoiceSelected = {},
            onProviderSelected = {},
            onApiKeyChanged = {},
            onDiscoverModels = {},
            onModelSelected = {},
            onSkipMedia = {},
            onSaveTtsProvider = {},
            onSaveMediaProvider = {},
            onCompleteOnboarding = {},
        )
    }
}
