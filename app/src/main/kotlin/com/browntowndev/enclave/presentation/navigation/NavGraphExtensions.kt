package com.browntowndev.enclave.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

internal const val NAV_ANIMATION_DURATION_MS = 300

internal fun getRouteLevel(route: String?): Int {
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

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.hierarchicalEnterTransition(): EnterTransition {
    val initialLevel = getRouteLevel(initialState.destination.route)
    val targetLevel = getRouteLevel(targetState.destination.route)
    return if (initialLevel < targetLevel) {
        slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(NAV_ANIMATION_DURATION_MS),
        )
    } else {
        slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(NAV_ANIMATION_DURATION_MS),
        )
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.hierarchicalExitTransition(): ExitTransition {
    val targetLevel = getRouteLevel(targetState.destination.route)
    val currentLevel = getRouteLevel(initialState.destination.route)
    return if (targetLevel > currentLevel) {
        slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(NAV_ANIMATION_DURATION_MS),
        )
    } else {
        slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(NAV_ANIMATION_DURATION_MS),
        )
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.hierarchicalPopEnterTransition(): EnterTransition {
    val initialLevel = getRouteLevel(initialState.destination.route)
    val targetLevel = getRouteLevel(targetState.destination.route)
    return if (initialLevel < targetLevel) {
        slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(NAV_ANIMATION_DURATION_MS),
        )
    } else {
        slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(NAV_ANIMATION_DURATION_MS),
        )
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.hierarchicalPopExitTransition(): ExitTransition {
    val targetLevel = getRouteLevel(targetState.destination.route)
    val currentLevel = getRouteLevel(initialState.destination.route)
    return if (targetLevel > currentLevel) {
        slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(NAV_ANIMATION_DURATION_MS),
        )
    } else {
        slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(NAV_ANIMATION_DURATION_MS),
        )
    }
}

internal fun slideInFromEndTransition(): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(NAV_ANIMATION_DURATION_MS),
    )

internal fun slideOutToStartTransition(): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = tween(NAV_ANIMATION_DURATION_MS),
    )
