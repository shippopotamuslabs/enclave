package com.browntowndev.enclave.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveDisabledActionTooltip
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveIconTile
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveMediaActionButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveModalBottomSheet
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSheetHeader
import com.browntowndev.enclave.core.ui.component.enclave.enclaveBorderColor
import com.browntowndev.enclave.core.ui.component.enclave.enclaveSurface
import com.browntowndev.enclave.core.ui.component.enclave.rememberNeoDisabledClick
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens

const val FULLSCREEN_MEDIA_ANIMATE_DISABLED_TOOLTIP =
    "Your video model only supports text-to-video. Set an image-to-video model in Settings to animate."

const val FULLSCREEN_MEDIA_MORE_ACTIONS_LABEL = "More actions"

enum class FullscreenMediaActionPlacement {
    PrimaryBar,
    SheetOnly,
}

data class FullscreenMediaAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val disabledTooltip: String? = null,
    val isDestructive: Boolean = false,
    val placement: FullscreenMediaActionPlacement = FullscreenMediaActionPlacement.SheetOnly,
)

internal fun partitionFullscreenMediaActions(
    actions: List<FullscreenMediaAction>,
): Pair<List<FullscreenMediaAction>, List<FullscreenMediaAction>> {
    val primary = actions.filter { it.placement == FullscreenMediaActionPlacement.PrimaryBar }
    val sheet = actions.filter { it.placement == FullscreenMediaActionPlacement.SheetOnly }
    return primary to sheet
}

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
    pageContent: @Composable (page: Int) -> Unit,
    pageActionsOverlay: @Composable (page: Int, overlayModifier: Modifier) -> Unit,
    floatingBottomContent: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
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
    var showActionsSheet by remember { mutableStateOf(false) }
    val (primaryActions, sheetActions) = remember(actions) { partitionFullscreenMediaActions(actions) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                    ),
                ).padding(24.dp),
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 3,
            fontWeight = FontWeight.Medium,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            primaryActions.forEach { action ->
                FullscreenMediaActionButton(
                    action = action,
                    showLabel = false,
                )
            }
            if (sheetActions.isNotEmpty()) {
                Spacer(modifier = Modifier.weight(1f))
                FullscreenMediaMoreButton(onClick = { showActionsSheet = true })
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showActionsSheet && sheetActions.isNotEmpty()) {
        FullscreenMediaActionsSheet(
            actions = sheetActions,
            onDismiss = { showActionsSheet = false },
        )
    }
}

@Composable
private fun FullscreenMediaMoreButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EnclaveMediaActionButton(
        icon = Icons.Default.MoreHoriz,
        label = FULLSCREEN_MEDIA_MORE_ACTIONS_LABEL,
        onClick = onClick,
        modifier = modifier,
        showLabel = false,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenMediaActionsSheet(
    actions: List<FullscreenMediaAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val regularActions = actions.filter { !it.isDestructive }
    val destructiveActions = actions.filter { it.isDestructive }

    EnclaveModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        EnclaveSheetHeader(
            title = "Actions",
            onDismiss = onDismiss,
        )
        Spacer(modifier = Modifier.height(EnclaveNeoTokens.rowSpacing))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing),
        ) {
            regularActions.forEach { action ->
                FullscreenMediaSheetActionRow(
                    action = action,
                    onClick = {
                        onDismiss()
                        action.onClick()
                    },
                )
            }
            if (destructiveActions.isNotEmpty() && regularActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
            }
            destructiveActions.forEach { action ->
                FullscreenMediaSheetActionRow(
                    action = action,
                    onClick = {
                        onDismiss()
                        action.onClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun FullscreenMediaSheetActionRow(
    action: FullscreenMediaAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EnclaveDisabledActionTooltip(
        enabled = action.enabled,
        disabledReason = action.disabledTooltip,
        modifier = modifier.fillMaxWidth(),
    ) {
        val handleClick = rememberNeoDisabledClick(enabled = action.enabled, onEnabledClick = onClick)
        val contentAlpha = if (action.enabled) 1f else 0.38f
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .alpha(contentAlpha)
                    .enclaveSurface(
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        borderColor = enclaveBorderColor(),
                        withShadow = false,
                    ).clickable(enabled = action.enabled, onClick = handleClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FullscreenMediaSheetLeadingIcon(
                    icon = action.icon,
                    contentDescription = action.label,
                    isDestructive = action.isDestructive,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = fullscreenMediaSheetLabelColor(isDestructive = action.isDestructive),
                    )
                    if (!action.enabled && !action.disabledTooltip.isNullOrBlank()) {
                        Text(
                            text = action.disabledTooltip,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenMediaSheetLeadingIcon(
    icon: ImageVector,
    contentDescription: String?,
    isDestructive: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isDestructive) {
        Box(
            modifier =
                modifier
                    .size(EnclaveNeoTokens.iconTileSize)
                    .enclaveSurface(
                        backgroundColor = MaterialTheme.colorScheme.errorContainer,
                        borderColor = enclaveBorderColor(),
                        withShadow = false,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    } else {
        EnclaveIconTile(
            icon = icon,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}

@Composable
private fun fullscreenMediaSheetLabelColor(isDestructive: Boolean): Color =
    if (isDestructive) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenMediaActionButton(
    action: FullscreenMediaAction,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    EnclaveDisabledActionTooltip(
        enabled = action.enabled,
        disabledReason = action.disabledTooltip,
        modifier = modifier,
    ) {
        val handleClick = rememberNeoDisabledClick(enabled = action.enabled, onEnabledClick = action.onClick)
        EnclaveMediaActionButton(
            icon = action.icon,
            label = action.label,
            onClick = handleClick,
            enabled = action.enabled,
            isDestructive = action.isDestructive,
            showLabel = showLabel,
        )
    }
}
