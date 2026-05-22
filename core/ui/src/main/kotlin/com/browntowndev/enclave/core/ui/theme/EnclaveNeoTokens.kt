package com.browntowndev.enclave.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared neo-brutalist layout tokens for Enclave surfaces.
 *
 * Colors always come from [androidx.compose.material3.MaterialTheme.colorScheme];
 * this object only defines geometry and spacing so settings, sheets, and future
 * screens stay consistent.
 */
object EnclaveNeoTokens {
    /** App-wide corner radius for neo panels, chips, and icon wells (chat may stay rounder until migrated). */
    val cornerRadius: Dp = 4.dp

    val shape = RoundedCornerShape(cornerRadius)

    /**
     * User chat bubble — speech/thought tail on the bottom-end corner (matches shared chat HTML:
     * `border-radius: 20px 20px 4px 20px` — slightly reduced in-app for balance).
     */
    val userBubbleCornerLarge: Dp = 16.dp

    val userBubbleCornerTail: Dp = 3.dp

    val userBubbleShape =
        RoundedCornerShape(
            topStart = userBubbleCornerLarge,
            topEnd = userBubbleCornerLarge,
            bottomEnd = userBubbleCornerTail,
            bottomStart = userBubbleCornerLarge,
        )

    /** Hard shadow under user bubbles (shared chat HTML uses 6px). */
    val userBubbleShadowOffset: Dp = 6.dp

    val userBubbleContentPadding: Dp = 20.dp

    /** Bottom-docked input bar (rounded top edge only). */
    val inputBarShape =
        RoundedCornerShape(
            topStart = cornerRadius,
            topEnd = cornerRadius,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        )

    /** Modal bottom sheets (rounded top edge only). */
    val bottomSheetShape =
        RoundedCornerShape(
            topStart = cornerRadius,
            topEnd = cornerRadius,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        )

    val borderWidth: Dp = 2.dp

    val trailingActionSize: Dp = 48.dp

    /** Hard shadow offset for elevated controls on light backgrounds. */
    val buttonShadowOffsetLight: Dp = 2.dp

    /** Larger offset on dark backgrounds so hard shadows read against charcoal. */
    val buttonShadowOffsetDark: Dp = 5.dp

    /** Vertical gap between standalone settings rows in a section. */
    val rowSpacing: Dp = 4.dp

    val iconTileSize: Dp = 40.dp

    val iconButtonSize: Dp = 44.dp

    val screenHorizontalPadding: Dp = 20.dp

    val sectionSpacing: Dp = 16.dp

    val sectionHeaderLetterSpacing: TextUnit = 0.8.sp
}
