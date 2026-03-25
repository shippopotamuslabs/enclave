package com.browntowndev.pocketcrew.feature.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.core.ui.R
import com.browntowndev.pocketcrew.core.ui.component.ShimmerText
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    onMenuClick: () -> Unit,
    onNewChatClick: () -> Unit,
    isThinking: Boolean
) {
    CenterAlignedTopAppBar(
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open history"
                )
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShimmerText(
                    text = "Pocket Crew",
                    baseColor = MaterialTheme.colorScheme.onBackground,
                    highlightColor = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                    enabled = isThinking,
                )
            }
        },
        actions = {
            Icon(
                painter = painterResource(R.drawable.edit_square),
                contentDescription = "Start new chat",
                modifier = Modifier
                    .size(40.dp)
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    )
}

@Preview
@Composable
private fun PreviewChatTopBarLight() {
    PocketCrewTheme {
        ChatTopBar({}, {}, false)
    }
}

@Preview
@Composable
private fun PreviewChatTopBarDark() {
    PocketCrewTheme(darkTheme = true) {
        ChatTopBar({}, {}, false)
    }
}

@Preview
@Composable
private fun PreviewChatTopBarThinking() {
    PocketCrewTheme {
        ChatTopBar({}, {}, true)
    }
}

@Preview
@Composable
private fun PreviewChatTopBarDynamic() {
    PocketCrewTheme(dynamicColor = true) {
        ChatTopBar({}, {}, true)
    }
}
