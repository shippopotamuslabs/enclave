package com.browntowndev.pocketcrew.feature.chat.components.thinkingindicator

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
private fun PreviewTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        Surface(color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
internal fun PreviewThinkingIndicatorEmpty() {
    PreviewTheme {
        ThinkingIndicator(
            elapsedSeconds = 0,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
internal fun PreviewThinkingIndicatorWithElapsedTime() {
    PreviewTheme {
        ThinkingIndicator(
            elapsedSeconds = 42,
            modifier = Modifier.padding(16.dp),
        )
    }
}
