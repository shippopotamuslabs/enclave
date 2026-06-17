package com.browntowndev.enclave.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.feature.download.DownloadPlanRefresher
import com.browntowndev.enclave.feature.download.ModelDownloadScreen

fun NavGraphBuilder.downloadGraph(
    modelsResult: DownloadModelsResult?,
    errorMessage: String?,
    onReady: () -> Unit,
    refreshDownloadPlan: DownloadPlanRefresher? = null,
) {
    composable(
        route = Routes.MODEL_DOWNLOAD,
        enterTransition = { slideInFromEndTransition() },
        exitTransition = { slideOutToStartTransition() },
    ) {
        ModelDownloadScreen(
            modelsResult = modelsResult,
            errorMessage = errorMessage,
            onReady = onReady,
            refreshDownloadPlan = refreshDownloadPlan,
        )
    }
}
