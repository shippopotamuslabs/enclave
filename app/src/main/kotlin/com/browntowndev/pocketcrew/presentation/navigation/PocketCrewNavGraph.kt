@file:Suppress("ktlint:standard:function-naming")

package com.browntowndev.pocketcrew.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.browntowndev.pocketcrew.domain.model.download.DownloadModelsResult
import com.browntowndev.pocketcrew.feature.chat.ChatRoute
import com.browntowndev.pocketcrew.feature.download.ModelDownloadScreen
import com.browntowndev.pocketcrew.feature.history.HistoryRoute
import com.browntowndev.pocketcrew.feature.settings.navigation.settingsGraph
import com.browntowndev.pocketcrew.feature.studio.GalleryDetailScreen
import com.browntowndev.pocketcrew.feature.studio.GalleryRoute
import com.browntowndev.pocketcrew.feature.studio.GalleryViewModel
import com.browntowndev.pocketcrew.feature.studio.MultimodalViewModel
import com.browntowndev.pocketcrew.feature.studio.StudioDetailScreen
import com.browntowndev.pocketcrew.feature.studio.StudioScreen

private const val ANIMATION_DURATION = 300

@Composable
fun PocketCrewNavGraph(
    navController: NavHostController,
    onShowSnackbar: (message: String, actionLabel: String?) -> Unit,
    startDestination: String,
    modelsResult: DownloadModelsResult?,
    errorMessage: String? = null,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(
            route = Routes.MODEL_DOWNLOAD,
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(ANIMATION_DURATION),
                )
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(ANIMATION_DURATION),
                )
            },
        ) {
            ModelDownloadScreen(
                modelsResult = modelsResult,
                errorMessage = errorMessage,
                onReady = {
                    navController.navigate(Routes.CHAT) {
                        popUpTo(Routes.MODEL_DOWNLOAD) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.CHAT_WITH_ID,
            arguments =
                listOf(
                    navArgument("chatId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            deepLinks =
                listOf(
                    navDeepLink {
                        uriPattern = Routes.CHAT_DEEP_LINK_PATTERN
                    },
                ),
            enterTransition = {
                val initialLevel = getRouteLevel(initialState.destination.route)
                val targetLevel = getRouteLevel(targetState.destination.route)
                if (initialLevel < targetLevel) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            exitTransition = {
                val targetLevel = getRouteLevel(targetState.destination.route)
                val currentLevel = getRouteLevel(initialState.destination.route)
                if (targetLevel > currentLevel) {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            popEnterTransition = {
                val initialLevel = getRouteLevel(initialState.destination.route)
                val targetLevel = getRouteLevel(targetState.destination.route)
                if (initialLevel < targetLevel) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            popExitTransition = {
                val targetLevel = getRouteLevel(targetState.destination.route)
                val currentLevel = getRouteLevel(initialState.destination.route)
                if (targetLevel > currentLevel) {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
        ) {
            ChatRoute(
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNewChat = {
                    navController.navigate(Routes.CHAT) {
                        popUpTo(Routes.CHAT_WITH_ID) { inclusive = true }
                    }
                },
                onShowSnackbar = onShowSnackbar,
            )
        }
        composable(
            route = Routes.HISTORY,
            enterTransition = {
                val initialLevel = getRouteLevel(initialState.destination.route)
                val targetLevel = getRouteLevel(targetState.destination.route)
                if (initialLevel < targetLevel) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            exitTransition = {
                val targetLevel = getRouteLevel(targetState.destination.route)
                val currentLevel = getRouteLevel(initialState.destination.route)
                if (targetLevel > currentLevel) {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            popEnterTransition = {
                val initialLevel = getRouteLevel(initialState.destination.route)
                val targetLevel = getRouteLevel(targetState.destination.route)
                if (initialLevel < targetLevel) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            popExitTransition = {
                val targetLevel = getRouteLevel(targetState.destination.route)
                val currentLevel = getRouteLevel(initialState.destination.route)
                if (targetLevel > currentLevel) {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
        ) {
            HistoryRoute(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChat = { id ->
                    if (id != null) {
                        navController.navigate(Routes.CHAT_WITH_ID.replace("{chatId}", id.value))
                    } else {
                        navController.navigate(Routes.CHAT)
                    }
                },
                onNavigateToStudio = { navController.navigate(Routes.STUDIO) },
                onNavigateToGallery = { navController.navigate(Routes.GALLERY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS_GRAPH) },
                onShowSnackbar = onShowSnackbar,
            )
        }

        composable(
            route = Routes.STUDIO_WITH_ARGS,
            arguments =
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
                ),
            enterTransition = {
                val initialLevel = getRouteLevel(initialState.destination.route)
                val targetLevel = getRouteLevel(targetState.destination.route)
                if (initialLevel < targetLevel) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            exitTransition = {
                val targetLevel = getRouteLevel(targetState.destination.route)
                val currentLevel = getRouteLevel(initialState.destination.route)
                if (targetLevel > currentLevel) {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            popEnterTransition = {
                val initialLevel = getRouteLevel(initialState.destination.route)
                val targetLevel = getRouteLevel(targetState.destination.route)
                if (initialLevel < targetLevel) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            popExitTransition = {
                val targetLevel = getRouteLevel(targetState.destination.route)
                val currentLevel = getRouteLevel(initialState.destination.route)
                if (targetLevel > currentLevel) {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
        ) { backStackEntry ->
            val studioViewModel = hiltViewModel<MultimodalViewModel>()
            val editAssetId = backStackEntry.arguments?.getString("editAssetId")
            val animateAssetId = backStackEntry.arguments?.getString("animateAssetId")

            LaunchedEffect(editAssetId, animateAssetId) {
                if (editAssetId != null) {
                    studioViewModel.onEditMedia(editAssetId)
                } else if (animateAssetId != null) {
                    studioViewModel.onAnimateMedia(animateAssetId)
                }
            }

            StudioScreen(
                viewModel = studioViewModel,
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToGallery = { navController.navigate(Routes.GALLERY) },
                onMediaClick = { assetId ->
                    navController.navigate(Routes.STUDIO_DETAIL.replace("{assetId}", assetId))
                },
                onShowSnackbar = onShowSnackbar,
            )
        }

        composable(
            route = Routes.GALLERY,
            enterTransition = {
                val initialLevel = getRouteLevel(initialState.destination.route)
                val targetLevel = getRouteLevel(targetState.destination.route)
                if (initialLevel < targetLevel) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            exitTransition = {
                val targetLevel = getRouteLevel(targetState.destination.route)
                val currentLevel = getRouteLevel(initialState.destination.route)
                if (targetLevel > currentLevel) {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            popEnterTransition = {
                val initialLevel = getRouteLevel(initialState.destination.route)
                val targetLevel = getRouteLevel(targetState.destination.route)
                if (initialLevel < targetLevel) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            popExitTransition = {
                val targetLevel = getRouteLevel(targetState.destination.route)
                val currentLevel = getRouteLevel(initialState.destination.route)
                if (targetLevel > currentLevel) {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
        ) {
            GalleryRoute(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { albumId, assetId ->
                    navController.navigate(
                        Routes.GALLERY_DETAIL
                            .replace("{albumId}", albumId)
                            .replace("{assetId}", assetId),
                    )
                },
            )
        }

        composable(
            route = Routes.GALLERY_DETAIL,
            arguments =
                listOf(
                    navArgument("albumId") { type = NavType.StringType },
                    navArgument("assetId") { type = NavType.StringType },
                ),
            enterTransition = {
                val initialLevel = getRouteLevel(initialState.destination.route)
                val targetLevel = getRouteLevel(targetState.destination.route)
                if (initialLevel < targetLevel) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            exitTransition = {
                val targetLevel = getRouteLevel(targetState.destination.route)
                val currentLevel = getRouteLevel(initialState.destination.route)
                if (targetLevel > currentLevel) {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            popEnterTransition = {
                val initialLevel = getRouteLevel(initialState.destination.route)
                val targetLevel = getRouteLevel(targetState.destination.route)
                if (initialLevel < targetLevel) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            popExitTransition = {
                val targetLevel = getRouteLevel(targetState.destination.route)
                val currentLevel = getRouteLevel(initialState.destination.route)
                if (targetLevel > currentLevel) {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getString("albumId") ?: ""
            val assetId = backStackEntry.arguments?.getString("assetId") ?: ""
            val galleryViewModel = hiltViewModel<GalleryViewModel>()
            val galleryUiState by galleryViewModel.uiState.collectAsStateWithLifecycle()

            GalleryDetailScreen(
                albumId = albumId,
                assetId = assetId,
                albums = galleryUiState.albums,
                onNavigateBack = { navController.popBackStack() },
                onShareMedia = galleryViewModel::shareSingleMedia,
                onSendToStudio = { id, mode ->
                    val targetRoute =
                        if (mode == "edit") Routes.studioWithEditAsset(id) else Routes.studioWithAnimateAsset(id)
                    navController.navigate(targetRoute) {
                        popUpTo(Routes.STUDIO) { inclusive = true }
                    }
                },
                onDeleteMedia = { id ->
                    galleryViewModel.deleteMedia(setOf(id))
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Routes.STUDIO_DETAIL,
            arguments = listOf(navArgument("assetId") { type = NavType.StringType }),
            enterTransition = {
                val initialLevel = getRouteLevel(initialState.destination.route)
                val targetLevel = getRouteLevel(targetState.destination.route)
                if (initialLevel < targetLevel) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            exitTransition = {
                val targetLevel = getRouteLevel(targetState.destination.route)
                val currentLevel = getRouteLevel(initialState.destination.route)
                if (targetLevel > currentLevel) {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            popEnterTransition = {
                val initialLevel = getRouteLevel(initialState.destination.route)
                val targetLevel = getRouteLevel(targetState.destination.route)
                if (initialLevel < targetLevel) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
            popExitTransition = {
                val targetLevel = getRouteLevel(targetState.destination.route)
                val currentLevel = getRouteLevel(initialState.destination.route)
                if (targetLevel > currentLevel) {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(ANIMATION_DURATION),
                    )
                }
            },
        ) { backStackEntry ->
            val assetId = backStackEntry.arguments?.getString("assetId") ?: ""
            // Use the studio's backstack entry to share the ViewModel
            val studioBackStackEntry =
                remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.STUDIO)
                }
            val studioViewModel = hiltViewModel<MultimodalViewModel>(studioBackStackEntry)
            val studioUiState by studioViewModel.uiState.collectAsStateWithLifecycle()

            StudioDetailScreen(
                assetId = assetId,
                assets = studioUiState.gallery,
                onNavigateBack = { navController.popBackStack() },
                onEditMedia = studioViewModel::onEditMedia,
                onAnimateMedia = { id, autoAnimate ->
                    studioViewModel.onAnimateMedia(id, autoAnimate)
                    navController.popBackStack(Routes.STUDIO, inclusive = false)
                },
                onShareMedia = studioViewModel::shareSingleMedia,
                onDeleteMedia = studioViewModel::deleteMedia,
                videoGenerationState = studioUiState.videoGenerationState,
            )
        }

        settingsGraph(
            navController = navController,
            onShowSnackbar = onShowSnackbar,
        )
    }
}

private fun getRouteLevel(route: String?): Int {
    if (route == null) return -1
    return when {
        route.startsWith(Routes.SETTINGS_GRAPH) || route.startsWith(Routes.SETTINGS_MAIN) -> 6
        route.startsWith(Routes.GALLERY_DETAIL.substringBefore("?")) -> 5
        route.startsWith(Routes.GALLERY) -> 4
        route.startsWith(Routes.STUDIO_DETAIL.substringBefore("?")) -> 3
        route.startsWith(Routes.STUDIO) -> 2
        route.startsWith(Routes.CHAT) -> 1
        route.startsWith(Routes.HISTORY) -> 0
        route == Routes.MODEL_DOWNLOAD -> -1
        else -> -1
    }
}
