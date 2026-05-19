package com.browntowndev.pocketcrew.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.feature.download.ModelDownloadScreen

fun NavGraphBuilder.downloadGraph(
    modelsResult: DownloadModelsResult?,
    errorMessage: String?,
    onReady: () -> Unit,
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
        )
    }
}
