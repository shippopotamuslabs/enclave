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

/**
 * Top app bar chrome: extends [color] behind the status bar, pads content below it, bottom rule.
 */
@Composable
fun EnclaveAppBarChrome(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable RowScope.() -> Unit,
) {
    EnclaveSystemBarStyle(color = color)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color,
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
