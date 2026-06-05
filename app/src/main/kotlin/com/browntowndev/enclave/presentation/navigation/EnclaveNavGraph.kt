package com.browntowndev.enclave.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.feature.download.DownloadPlanRefresher
import com.browntowndev.enclave.feature.onboarding.ModelChoice

@Composable
fun EnclaveNavGraph(
    navController: NavHostController,
    onShowSnackbar: (message: String, actionLabel: String?, onActionClick: (() -> Unit)?) -> Unit,
    startDestination: String,
    modelsResult: DownloadModelsResult?,
    errorMessage: String? = null,
    refreshDownloadPlan: DownloadPlanRefresher? = null,
) {
    val chatActions =
        ChatNavActions(
            onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
            onNewChatFromChat = {
                navController.navigate(Routes.CHAT) {
                    popUpTo(Routes.CHAT_WITH_ID) { inclusive = true }
                }
            },
            onNewChatFromShared = {
                navController.navigate(Routes.CHAT) {
                    popUpTo(Routes.SHARED_CHAT) { inclusive = true }
                }
            },
            onNavigateToStudio = { ids, uris, types, prompts ->
                navController.navigate(Routes.studioWithRehydration(ids, uris, types, prompts))
            },
            onShowSnackbar = onShowSnackbar,
            onNavigateToImportedChat = { chatId ->
                navController.navigate(
                    Routes.CHAT_WITH_ID.replace("{chatId}", chatId.value),
                ) {
                    popUpTo(Routes.SHARED_CHAT) { inclusive = true }
                }
            },
        )

    val historyActions =
        HistoryNavActions(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToChat = { id ->
                if (id != null) {
                    navController.navigate(Routes.CHAT_WITH_ID.replace("{chatId}", id.value)) {
                        openChatFromHistory()
                    }
                } else {
                    navController.navigate(Routes.CHAT)
                }
            },
            onNavigateToStudio = { navController.navigate(Routes.STUDIO) },
            onNavigateToGallery = { navController.navigate(Routes.GALLERY) },
            onNavigateToSettings = { navController.navigate(Routes.SETTINGS_GRAPH) },
        )

    val studioActions =
        StudioNavActions(
            onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
            onNavigateToGallery = { navController.navigate(Routes.GALLERY) },
            onNavigateBack = { navController.popBackStack() },
            onNavigateToStudioDetail = { assetId ->
                navController.navigate(Routes.STUDIO_DETAIL.replace("{assetId}", assetId))
            },
            onNavigateToGalleryDetail = { albumId, assetId ->
                navController.navigate(
                    Routes.GALLERY_DETAIL
                        .replace("{albumId}", albumId)
                        .replace("{assetId}", assetId),
                )
            },
            onSendToStudioFromGallery = { id, mode, prompt ->
                val targetRoute =
                    when (mode) {
                        "edit" -> Routes.studioWithEditAsset(id)
                        "animate" -> Routes.studioDetailWithAnimateAsset(id, autoAnimate = prompt != null, prompt = prompt)
                        "quick_animate" -> Routes.studioDetailWithAnimateAsset(id, autoAnimate = true)
                        else -> Routes.studioWithEditAsset(id)
                    }
                navController.navigate(targetRoute) {
                    popUpTo(Routes.STUDIO) { inclusive = true }
                }
            },
            onShowSnackbar = onShowSnackbar,
        )

    // Tracks the effective models result for the download screen. Normally equals the startup
    // modelsResult, but is filtered to utility-only assets when API onboarding completes so
    // the download screen doesn't enqueue local LLM downloads for API users.
    var effectiveModelsResult by remember(modelsResult) { mutableStateOf(modelsResult) }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        onboardingNavGraph(
            onOnboardingComplete = { modelChoice ->
                val filtered =
                    when (modelChoice) {
                        ModelChoice.API ->
                            modelsResult?.let(::filterDownloadModelsResultForApiOnboarding)
                        ModelChoice.LOCAL,
                        ModelChoice.RESTORED,
                        ModelChoice.UNDECIDED,
                        -> modelsResult
                    }
                effectiveModelsResult = filtered

                val needsDownload = errorMessage != null || filtered?.modelsToDownload?.isNotEmpty() == true
                if (needsDownload) {
                    navController.navigate(Routes.MODEL_DOWNLOAD) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                } else {
                    navController.navigate(Routes.CHAT) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            },
        )
        downloadGraph(
            modelsResult = effectiveModelsResult,
            errorMessage = errorMessage,
            onReady = {
                navController.navigate(Routes.CHAT) {
                    popUpTo(Routes.MODEL_DOWNLOAD) { inclusive = true }
                }
            },
            refreshDownloadPlan = refreshDownloadPlan,
        )
        chatNavGraph(
            actions = chatActions,
            historyActions = historyActions,
        )
        studioNavGraph(
            navController = navController,
            actions = studioActions,
        )
        settingsNavGraph(navController)
        profileBuilderNavGraph(navController)
    }
}
