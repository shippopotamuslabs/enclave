package com.browntowndev.enclave.feature.chat.components.thinkingindicator

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
internal fun PreviewThinkingIndicatorEmpty() {
    EnclaveTheme(darkTheme = true) {
        ThinkingIndicator(
            elapsedSeconds = 0,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
internal fun PreviewThinkingIndicatorWithElapsedTime() {
    EnclaveTheme(darkTheme = true) {
        ThinkingIndicator(
            elapsedSeconds = 42,
            modifier = Modifier.padding(16.dp),
        )
    }
}
