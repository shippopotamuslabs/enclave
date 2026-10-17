package com.browntowndev.enclave.core.ui.component.enclave

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveThemeAccessor
import dev.chrisbanes.haze.HazeState

/**
 * Top app bar chrome: extends [color] behind the status bar, pads content below it, bottom rule.
 * When [hazeState] is supplied and the active spec enables glass, the bar renders as a
 * translucent blur over the haze source instead of a solid [color] surface.
 */
@Composable
fun EnclaveAppBarChrome(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    hazeState: HazeState? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val glassEnabled = EnclaveThemeAccessor.glass.enabled && hazeState != null
    EnclaveSystemBarStyle(color = color)
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (glassEnabled) Modifier.enclaveGlassEffect(hazeState) else Modifier),
        color = if (glassEnabled) Color.Transparent else color,
    ) {
        Column {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(
                            horizontal = EnclaveNeoTokens.screenHorizontalPadding,
                            vertical = 8.dp,
                        ),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                content = content,
            )
            HorizontalDivider(
                thickness = EnclaveNeoTokens.borderWidth,
                color = enclaveBorderColor(),
            )
        }
    }
}
