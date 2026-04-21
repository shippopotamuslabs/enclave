package com.browntowndev.pocketcrew.core.ui.component.sheet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity

/**
 * A wrapper around [ModalBottomSheet] that provides a stable, "jump-free" experience.
 *
 * This component addresses a known Jetpack Compose Material 3 bug where bottom sheets
 * enter an infinite "jumping" animation loop when expanded to the top of the screen.
 * It also prevents jittering during flings by intercepting unconsumed velocity at
 * boundaries.
 *
 * @param onDismissRequest Called when the user dismisses the bottom sheet.
 * @param modifier Optional modifier for the bottom sheet.
 * @param sheetState The state of the bottom sheet.
 * @param shape The shape of the bottom sheet.
 * @param containerColor The color used for the background of this bottom sheet.
 * @param contentColor The preferred color for content inside this bottom sheet.
 * @param tonalElevation The tonal elevation of this bottom sheet.
 * @param scrimColor The color of the scrim that obscures content when the bottom sheet is open.
 * @param dragHandle Optional visual marker to indicate that the sheet can be swiped.
 * @param content The content to be displayed inside the bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpFreeModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = BottomSheetDefaults.Elevation,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit
) {
    // Stabilize layout by intercepting nested scroll flings that reach the boundary.
    // This prevents the "jitter/jumping" behavior in fully expanded state.
    val nestedScrollInterceptor = remember(sheetState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Only swallow upward flings if we are already at the top (Expanded).
                // This prevents the "jitter/bounce" bug at the top boundary.
                // We must allow flings in other states so the sheet can expand/collapse.
                val isAtTop = sheetState.currentValue == SheetValue.Expanded
                return if (source == NestedScrollSource.Fling && available.y < 0 && isAtTop) {
                    available
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Only swallow remaining upward velocity if we are already at the top.
                val isAtTop = sheetState.currentValue == SheetValue.Expanded
                return if (available.y < 0 && isAtTop) {
                    available
                } else {
                    Velocity.Zero
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.nestedScroll(nestedScrollInterceptor),
        sheetState = sheetState,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        dragHandle = dragHandle,
        // Use WindowInsets.ime and WindowInsets.statusBars to respect system bars and break the feedback loop.
        contentWindowInsets = { WindowInsets.ime.union(WindowInsets.statusBars) }
    ) {
        Column(
            modifier = Modifier.navigationBarsPadding()
        ) {
            content()
        }
    }
}
