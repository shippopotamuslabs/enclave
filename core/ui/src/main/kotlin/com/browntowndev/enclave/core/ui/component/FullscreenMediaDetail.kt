package com.browntowndev.enclave.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoMediaActionButton
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

const val FULLSCREEN_MEDIA_ANIMATE_DISABLED_TOOLTIP =
    "Setup a video model in Settings to enable."

data class FullscreenMediaAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val disabledTooltip: String? = null,
    val isDestructive: Boolean = false,
)

/**
 * Fullscreen media detail scaffold: black backdrop, transparent close bar, and paged content.
 * Use [FullscreenMediaPagerContent] inside [fullscreenMediaDetailContent] for the pager + overlays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenMediaDetailScaffold(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    emptyContent: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Asset not found", color = Color.White)
        }
    },
    hasContent: Boolean,
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.Black,
        topBar = { FullscreenMediaDetailTopBar(onClose = onClose) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        if (hasContent) {
            content(padding)
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                emptyContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenMediaDetailTopBar(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = { },
        navigationIcon = {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                navigationIconContentColor = Color.White,
            ),
    )
}

@Composable
fun FullscreenMediaPagerContent(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    hazeState: HazeState = rememberHazeState(),
    pageContent: @Composable (page: Int) -> Unit,
    pageActionsOverlay: @Composable (page: Int, overlayModifier: Modifier) -> Unit,
    floatingBottomContent: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            beyondViewportPageCount = 1,
        ) { page ->
            Box(modifier = Modifier.fillMaxSize()) {
                pageContent(page)
                pageActionsOverlay(page, Modifier.align(Alignment.BottomCenter))
            }
        }
        floatingBottomContent()
    }
}

@Composable
fun FullscreenMediaActionsOverlay(
    prompt: String,
    actions: List<FullscreenMediaAction>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    ),
                )
                .padding(24.dp),
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 3,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { action ->
                FullscreenMediaActionButton(action = action)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenMediaActionButton(
    action: FullscreenMediaAction,
    modifier: Modifier = Modifier,
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            if (!action.enabled && action.disabledTooltip != null) {
                PlainTooltip { Text(action.disabledTooltip) }
            }
        },
        state = tooltipState,
        enableUserInput = false,
    ) {
        NeoMediaActionButton(
            icon = action.icon,
            label = action.label,
            onClick = {
                if (action.enabled) {
                    action.onClick()
                } else if (action.disabledTooltip != null) {
                    scope.launch { tooltipState.show() }
                }
            },
            enabled = action.enabled,
            isDestructive = action.isDestructive,
            modifier = modifier,
        )
    }
}
