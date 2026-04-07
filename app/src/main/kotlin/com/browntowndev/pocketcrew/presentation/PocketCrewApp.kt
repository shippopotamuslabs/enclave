package com.browntowndev.pocketcrew.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.presentation.navigation.PocketCrewNavGraph
import com.browntowndev.pocketcrew.presentation.navigation.Routes
import kotlinx.coroutines.launch

@Suppress("FunctionNaming")
@Composable
fun PocketCrewApp(
    viewModel: PocketCrewAppViewModel = hiltViewModel(),
    initialRoute: String = Routes.MODEL_DOWNLOAD,
    modelsResult: DownloadModelsResult?,
    errorMessage: String? = null,
) {
    val themeUiState by viewModel.themeUiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.errorHandler) {
        viewModel.errorHandler.errorEvents.collect { message ->
            launch {
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    PocketCrewTheme(
        darkTheme = themeUiState.darkTheme,
        dynamicColor = themeUiState.dynamicColor,
    ) {
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()

        Box(Modifier.fillMaxSize()) {
            PocketCrewNavGraph(
                navController = navController,
                startDestination = initialRoute,
                modelsResult = modelsResult,
                errorMessage = errorMessage,
                onShowSnackbar = { message, actionLabel ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = actionLabel,
                        )
                    }
                },
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
            )
        }
    }
}
