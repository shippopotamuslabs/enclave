package com.browntowndev.enclave.feature.settings.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

private const val SETTINGS_NAV_ANIMATION_DURATION_MS = 300

internal fun settingsEnterTransition() =
    slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(SETTINGS_NAV_ANIMATION_DURATION_MS),
    )

internal fun settingsExitTransition() =
    slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = tween(SETTINGS_NAV_ANIMATION_DURATION_MS),
    )

internal fun settingsPopEnterTransition() =
    slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(SETTINGS_NAV_ANIMATION_DURATION_MS),
    )

internal fun settingsPopExitTransition() =
    slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(SETTINGS_NAV_ANIMATION_DURATION_MS),
    )
