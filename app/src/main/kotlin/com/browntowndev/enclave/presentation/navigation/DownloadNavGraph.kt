package com.browntowndev.enclave.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.feature.download.DownloadPlanRefresher
import com.browntowndev.enclave.feature.download.ModelDownloadScreen
import com.browntowndev.enclave.feature.download.toUi

fun NavGraphBuilder.downloadGraph(
    modelsResultProvider: () -> DownloadModelsResult?,
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
            modelsResult = modelsResultProvider()?.toUi(),
            errorMessage = errorMessage,
            onReady = onReady,
            refreshDownloadPlan = refreshDownloadPlan,
        )
    }
}
