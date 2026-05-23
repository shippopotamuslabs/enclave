package com.browntowndev.enclave.core.ui.component.neo

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Paints the status bar with [color] so edge-to-edge app bars blend with system chrome.
 */
@Composable
fun NeoSystemBarStyle(
    color: Color = MaterialTheme.colorScheme.surface,
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = color.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
            color.luminance() > 0.5f
    }
}
