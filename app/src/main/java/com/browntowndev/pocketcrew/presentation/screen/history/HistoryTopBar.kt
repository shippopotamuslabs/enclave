package com.browntowndev.pocketcrew.presentation.screen.history

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTopBar(
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Back to chat"
                )
            }
        },
        title = {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                maxLines = 1,
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Pocket Crew History") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                }
            )
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Open settings"
                )
            }
        }
    )
}

@Preview
@Composable
fun PreviewHistoryTopBarLight() {
    PocketCrewTheme {
        HistoryTopBar({}, {})
    }
}

@Preview
@Composable
fun PreviewHistoryTopBarDark() {
    PocketCrewTheme(darkTheme = true) {
        HistoryTopBar({}, {})
    }
}

@Preview
@Composable
fun PreviewHistoryTopBarDynamic() {
    PocketCrewTheme(dynamicColor = true) {
        HistoryTopBar({}, {})
    }
}