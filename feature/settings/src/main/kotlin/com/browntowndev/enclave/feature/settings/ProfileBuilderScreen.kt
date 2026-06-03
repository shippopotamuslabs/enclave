package com.browntowndev.enclave.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.feature.settings.steps.AnalysisProgressStep
import com.browntowndev.enclave.feature.settings.steps.CostEstimateStep
import com.browntowndev.enclave.feature.settings.steps.FileImportStep
import com.browntowndev.enclave.feature.settings.steps.ModelSelectStep
import com.browntowndev.enclave.feature.settings.steps.ReviewEditStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileBuilderScreen(
    uiState: ProfileBuilderUiState,
    onBack: () -> Unit,
    onModelSelected: (com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId) -> Unit,
    onFilesSelected: (uris: List<String>, names: List<String>) -> Unit,
    onProceedFromFiles: () -> Unit,
    onStartAnalysis: () -> Unit,
    onProfileEdited: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onGoToStep: (Int) -> Unit,
) {
    val pagerState =
        rememberPagerState(
            initialPage = uiState.currentStep,
            pageCount = { PROFILE_BUILDER_TOTAL_STEPS },
        )

    LaunchedEffect(uiState.currentStep) {
        pagerState.animateScrollToPage(uiState.currentStep)
    }

    val filePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            val pairs =
                uris.map { uri ->
                    val name = uri.lastPathSegment ?: uri.toString()
                    uri.toString() to name
                }
            onFilesSelected(pairs.map { it.first }, pairs.map { it.second })
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Psychological Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            WizardStepBar(currentStep = uiState.currentStep, totalSteps = PROFILE_BUILDER_TOTAL_STEPS)

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    PROFILE_BUILDER_STEP_MODEL ->
                        ModelSelectStep(
                            models = uiState.configuredModels,
                            selectedConfigId = uiState.selectedConfigId,
                            onModelSelected = onModelSelected,
                            onNext = { onGoToStep(PROFILE_BUILDER_STEP_FILES) },
                        )
                    PROFILE_BUILDER_STEP_FILES ->
                        FileImportStep(
                            selectedFileNames = uiState.selectedFileNames,
                            errorMessage = uiState.errorMessage,
                            onPickFiles = { filePicker.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                            onBack = { onGoToStep(PROFILE_BUILDER_STEP_MODEL) },
                            onNext = onProceedFromFiles,
                            canProceed = uiState.canProceedFromStep1,
                        )
                    PROFILE_BUILDER_STEP_COST ->
                        CostEstimateStep(
                            isLoading = uiState.isPreparingCorpus,
                            estimate = uiState.costEstimate,
                            onBack = { onGoToStep(PROFILE_BUILDER_STEP_FILES) },
                            onStartAnalysis = onStartAnalysis,
                        )
                    PROFILE_BUILDER_STEP_PROGRESS ->
                        AnalysisProgressStep(
                            currentPass = uiState.currentPass,
                            totalPasses = uiState.totalPasses,
                            isComplete = uiState.isAnalysisComplete,
                            errorMessage = uiState.analysisError,
                            onRetry = onStartAnalysis,
                            onBack = { onGoToStep(PROFILE_BUILDER_STEP_COST) },
                        )
                    PROFILE_BUILDER_STEP_REVIEW ->
                        ReviewEditStep(
                            profileContent = uiState.profileContent,
                            isSaving = uiState.isSaving,
                            onContentEdited = onProfileEdited,
                            onSave = onSaveProfile,
                            onBack = { onGoToStep(PROFILE_BUILDER_STEP_PROGRESS) },
                        )
                }
            }
        }
    }
}

@Composable
private fun WizardStepBar(
    currentStep: Int,
    totalSteps: Int,
) {
    val primary = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(totalSteps) { step ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(
                            color = if (step <= currentStep) primary else dim,
                            shape = EnclaveNeoTokens.shape,
                        ),
            )
        }
    }
}
