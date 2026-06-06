package com.browntowndev.enclave.presentation

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.feature.download.DownloadPlanRefresher
import com.browntowndev.enclave.presentation.navigation.EnclaveNavGraph
import com.browntowndev.enclave.presentation.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun EnclaveApp(
    viewModel: EnclaveAppViewModel = hiltViewModel(),
    initialRoute: String = Routes.MODEL_DOWNLOAD,
    modelsResult: DownloadModelsResult?,
    errorMessage: String? = null,
    navigationIntent: Intent? = null,
    refreshDownloadPlan: DownloadPlanRefresher? = null,
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

    EnclaveTheme(
        darkTheme = themeUiState.darkTheme,
        dynamicColor = themeUiState.dynamicColor,
    ) {
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()

        LaunchedEffect(navController, navigationIntent) {
            if (navigationIntent?.action == Intent.ACTION_VIEW && navigationIntent.data != null) {
                navController.handleDeepLink(navigationIntent)
            }
        }

        Box(Modifier.fillMaxSize()) {
            EnclaveNavGraph(
                navController = navController,
                startDestination = initialRoute,
                modelsResult = modelsResult,
                errorMessage = errorMessage,
                refreshDownloadPlan = refreshDownloadPlan,
                onShowSnackbar = { message, actionLabel, onActionClick ->
                    scope.launch {
                        val result =
                            snackbarHostState.showSnackbar(
                                message = message,
                                actionLabel = actionLabel,
                                withDismissAction = actionLabel != null,
                            )
                        if (result == SnackbarResult.ActionPerformed) {
                            onActionClick?.invoke()
                        }
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
