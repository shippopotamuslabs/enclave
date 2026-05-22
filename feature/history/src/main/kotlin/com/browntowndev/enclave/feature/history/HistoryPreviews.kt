package com.browntowndev.enclave.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
@Preview(showBackground = true, name = "History — dark loaded")
@Composable
fun PreviewHistoryScreenDarkLoaded() {
    EnclaveTheme(darkTheme = true) {
        HistoryScreen(
            uiState = HistoryPreviewData.loadedUiState,
            searchQuery = "",
            onSearchQueryChange = {},
            onBackClick = {},
            onChatClick = {},
            onNewChatClick = {},
            onStudioClick = {},
            onGalleryClick = {},
            onDeleteChat = {},
            onRenameChat = { _, _ -> },
            onPinChat = {},
            onUnpinChat = {},
            onSettingsClick = {},
        )
    }
}

@Preview(showBackground = true, name = "History — dark loading")
@Composable
fun PreviewHistoryScreenDarkLoading() {
    EnclaveTheme(darkTheme = true) {
        HistoryScreen(
            uiState = HistoryPreviewData.loadingUiState,
            searchQuery = "",
            onSearchQueryChange = {},
            onBackClick = {},
            onChatClick = {},
            onNewChatClick = {},
            onStudioClick = {},
            onGalleryClick = {},
            onDeleteChat = {},
            onRenameChat = { _, _ -> },
            onPinChat = {},
            onUnpinChat = {},
            onSettingsClick = {},
        )
    }
}

@Preview(showBackground = true, name = "History — dark search")
@Composable
fun PreviewHistoryScreenDarkSearch() {
    EnclaveTheme(darkTheme = true) {
        HistoryScreen(
            uiState = HistoryPreviewData.loadedUiState,
            searchQuery = "vault",
            onSearchQueryChange = {},
            onBackClick = {},
            onChatClick = {},
            onNewChatClick = {},
            onStudioClick = {},
            onGalleryClick = {},
            onDeleteChat = {},
            onRenameChat = { _, _ -> },
            onPinChat = {},
            onUnpinChat = {},
            onSettingsClick = {},
        )
    }
}

@Preview(showBackground = true, name = "History top bar — dark")
@Composable
fun PreviewHistoryTopBarDark() {
    EnclaveTheme(darkTheme = true) {
        HistoryTopBar(
            searchQuery = "",
            onSearchQueryChange = {},
            onBackClick = {},
            onSettingsClick = {},
        )
    }
}

@Preview(showBackground = true, name = "History top bar — dark with query")
@Composable
fun PreviewHistoryTopBarDarkWithQuery() {
    EnclaveTheme(darkTheme = true) {
        HistoryTopBar(
            searchQuery = "vault",
            onSearchQueryChange = {},
            onBackClick = {},
            onSettingsClick = {},
        )
    }
}
