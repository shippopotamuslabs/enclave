package com.browntowndev.enclave.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.browntowndev.enclave.feature.studio.GalleryDetailScreen
import com.browntowndev.enclave.feature.studio.GalleryRoute
import com.browntowndev.enclave.feature.studio.GalleryViewModel
import com.browntowndev.enclave.feature.studio.MultimodalViewModel
import com.browntowndev.enclave.feature.studio.StudioDetailScreen
import com.browntowndev.enclave.feature.studio.StudioScreen
import kotlinx.serialization.json.Json

fun NavGraphBuilder.studioNavGraph(
    navController: NavHostController,
    actions: StudioNavActions,
) {
    composable(
        route = Routes.STUDIO_WITH_ARGS,
        arguments = studioWithArgsNavArguments(),
        enterTransition = { hierarchicalEnterTransition() },
        exitTransition = { hierarchicalExitTransition() },
        popEnterTransition = { hierarchicalPopEnterTransition() },
        popExitTransition = { hierarchicalPopExitTransition() },
    ) { backStackEntry ->
        StudioRoute(
            backStackEntry = backStackEntry,
            actions = actions,
        )
    }

    composable(
        route = Routes.GALLERY,
        enterTransition = { hierarchicalEnterTransition() },
        exitTransition = { hierarchicalExitTransition() },
        popEnterTransition = { hierarchicalPopEnterTransition() },
        popExitTransition = { hierarchicalPopExitTransition() },
    ) {
        GalleryRoute(
            onNavigateBack = actions.onNavigateBack,
            onNavigateToDetail = actions.onNavigateToGalleryDetail,
        )
    }

    composable(
        route = Routes.GALLERY_DETAIL,
        arguments =
            listOf(
                navArgument("albumId") { type = NavType.StringType },
                navArgument("assetId") { type = NavType.StringType },
            ),
        enterTransition = { hierarchicalEnterTransition() },
        exitTransition = { hierarchicalExitTransition() },
        popEnterTransition = { hierarchicalPopEnterTransition() },
        popExitTransition = { hierarchicalPopExitTransition() },
    ) { backStackEntry ->
        GalleryDetailRoute(
            backStackEntry = backStackEntry,
            actions = actions,
        )
    }

    composable(
        route = Routes.STUDIO_DETAIL,
        arguments = studioDetailNavArguments(),
        enterTransition = { hierarchicalEnterTransition() },
        exitTransition = { hierarchicalExitTransition() },
        popEnterTransition = { hierarchicalPopEnterTransition() },
        popExitTransition = { hierarchicalPopExitTransition() },
    ) { backStackEntry ->
        StudioDetailRoute(
            navController = navController,
            backStackEntry = backStackEntry,
            onNavigateBack = actions.onNavigateBack,
            actions = actions,
        )
    }
}

private fun studioWithArgsNavArguments() =
    listOf(
        navArgument("editAssetId") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("animateAssetId") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("animatePrompt") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("autoAnimate") {
            type = NavType.BoolType
            defaultValue = false
        },
        navArgument("rehydrateIds") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("rehydrateUris") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("rehydrateTypes") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("rehydratePrompts") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    )

private fun studioDetailNavArguments() =
    listOf(
        navArgument("assetId") { type = NavType.StringType },
        navArgument("animatePrompt") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
        navArgument("autoAnimate") {
            type = NavType.BoolType
            defaultValue = false
        },
    )

@Composable
private fun StudioRoute(
    backStackEntry: NavBackStackEntry,
    actions: StudioNavActions,
) {
    val studioViewModel = hiltViewModel<MultimodalViewModel>()
    val editAssetId = backStackEntry.arguments?.getString("editAssetId")
    val animateAssetId = backStackEntry.arguments?.getString("animateAssetId")
    val animatePrompt = backStackEntry.arguments?.getString("animatePrompt")
    val autoAnimate = backStackEntry.arguments?.getBoolean("autoAnimate") ?: false
    val rehydrateIds = backStackEntry.arguments?.getString("rehydrateIds")
    val rehydrateUris = backStackEntry.arguments?.getString("rehydrateUris")
    val rehydrateTypes = backStackEntry.arguments?.getString("rehydrateTypes")
    val rehydratePrompts = backStackEntry.arguments?.getString("rehydratePrompts")

    var isStudioInitialized by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(
        editAssetId,
        animateAssetId,
        animatePrompt,
        autoAnimate,
        rehydrateIds,
        rehydrateUris,
        rehydrateTypes,
        rehydratePrompts,
    ) {
        if (editAssetId != null || rehydrateUris != null) {
            val json = Json
            val ids = rehydrateIds?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()
            val uris = rehydrateUris?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()
            val types = rehydrateTypes?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()
            val prompts = rehydratePrompts?.let { json.decodeFromString<List<String>>(it) } ?: emptyList()

            studioViewModel.mediaActionsCoordinator.onEditMedia(
                prompt = studioViewModel.promptCoordinator,
                assetId = editAssetId,
                rehydrateIds = ids,
                rehydrateUris = uris,
                rehydrateTypes = types,
                rehydratePrompts = prompts,
            )
            isStudioInitialized = true
        } else if (animateAssetId != null) {
            studioViewModel.mediaActionsCoordinator.onAnimateMedia(
                prompt = studioViewModel.promptCoordinator,
                assetId = animateAssetId,
                autoAnimate = autoAnimate,
                customPrompt = animatePrompt,
            )
            isStudioInitialized = true
        } else if (!isStudioInitialized) {
            studioViewModel.clearSession()
            isStudioInitialized = true
        }
    }

    StudioScreen(
        viewModel = studioViewModel,
        onNavigateToHistory = actions.onNavigateToHistory,
        onNavigateToGallery = actions.onNavigateToGallery,
        onMediaClick = actions.onNavigateToStudioDetail,
        onShowSnackbar = actions.onShowSnackbar,
    )
}

@Composable
private fun GalleryDetailRoute(
    backStackEntry: NavBackStackEntry,
    actions: StudioNavActions,
) {
    val albumId = backStackEntry.arguments?.getString("albumId").orEmpty()
    val assetId = backStackEntry.arguments?.getString("assetId").orEmpty()
    val galleryViewModel = hiltViewModel<GalleryViewModel>()
    val galleryUiState by galleryViewModel.uiState.collectAsStateWithLifecycle()

    GalleryDetailScreen(
        albumId = albumId,
        assetId = assetId,
        albums = galleryUiState.albums,
        hasImageToVideoModel = galleryUiState.hasImageToVideoModel,
        onNavigateBack = actions.onNavigateBack,
        onShareMedia = galleryViewModel::shareSingleMedia,
        onSendToStudio = actions.onSendToStudioFromGallery,
        onDeleteMedia = { id ->
            galleryViewModel.deleteMedia(setOf(id))
            actions.onNavigateBack()
        },
    )
}

@Composable
private fun StudioDetailRoute(
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
    onNavigateBack: () -> Unit,
    actions: StudioNavActions,
) {
    val assetId = backStackEntry.arguments?.getString("assetId").orEmpty()
    val animatePrompt = backStackEntry.arguments?.getString("animatePrompt")
    val autoAnimate = backStackEntry.arguments?.getBoolean("autoAnimate") ?: false

    val studioBackStackEntry =
        remember(backStackEntry) {
            navController.getBackStackEntry(Routes.STUDIO_WITH_ARGS)
        }
    val studioViewModel = hiltViewModel<MultimodalViewModel>(studioBackStackEntry)
    val studioUiState by studioViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(assetId, animatePrompt, autoAnimate) {
        if (autoAnimate) {
            studioViewModel.mediaActionsCoordinator.onAnimateMedia(
                prompt = studioViewModel.promptCoordinator,
                assetId = assetId,
                autoAnimate = true,
                customPrompt = animatePrompt,
            )
        }
    }

    StudioDetailScreen(
        assetId = assetId,
        assets = studioUiState.gallery,
        onNavigateBack = onNavigateBack,
        onEditMedia = { id ->
            studioViewModel.mediaActionsCoordinator.onEditMedia(
                prompt = studioViewModel.promptCoordinator,
                assetId = id,
                fromUserAction = true,
            )
        },
        onAnimateMedia = { assetId, autoAnimate, customPrompt ->
            studioViewModel.mediaActionsCoordinator.onAnimateMedia(
                prompt = studioViewModel.promptCoordinator,
                assetId = assetId,
                autoAnimate = autoAnimate,
                customPrompt = customPrompt,
            )
        },
        onShareMedia = studioViewModel.mediaActionsCoordinator::shareSingleMedia,
        onDeleteMedia = studioViewModel.mediaActionsCoordinator::deleteMedia,
        onShowSnackbar = actions.onShowSnackbar,
        videoGenerationState = studioUiState.videoGenerationState,
        hasImageToVideoModel = studioUiState.hasImageToVideoModel,
    )
}
