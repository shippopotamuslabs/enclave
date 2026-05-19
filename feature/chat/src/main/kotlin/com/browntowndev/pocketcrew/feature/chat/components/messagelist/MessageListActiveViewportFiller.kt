package com.browntowndev.pocketcrew.feature.chat.components.messagelist

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun MessageListActiveViewportFiller(
    heightPx: Int,
    modifier: Modifier = Modifier,
) {
    if (heightPx <= 0) return
    val height = with(LocalDensity.current) { heightPx.toDp() }
    Spacer(modifier = modifier.height(height))
}
