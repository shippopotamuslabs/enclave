package com.browntowndev.pocketcrew.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState

@Composable
fun FullscreenMediaViewer(
    localUri: String,
    mediaType: MediaCapability,
    contentDescription: String,
    modifier: Modifier = Modifier,
    videoContent: @Composable (String, String, Modifier) -> Unit = { uri, description, videoModifier ->
        VideoPlayer(
            localUri = uri,
            contentDescription = description,
            modifier = videoModifier,
        )
    },
    audioContent: @Composable (String, String, Modifier) -> Unit = { uri, description, audioModifier ->
        AudioPlayer(
            localUri = uri,
            contentDescription = description,
            modifier = audioModifier,
        )
    },
    imageContent: @Composable (String, String, Modifier) -> Unit = { uri, description, imageModifier ->
        if (LocalInspectionMode.current) {
            FullscreenMediaPreviewPlaceholder(
                contentDescription = description,
                icon = Icons.Default.Image,
                modifier = imageModifier,
            )
        } else {
            val imageState = rememberZoomableImageState()
            ZoomableAsyncImage(
                model = uri,
                contentDescription = description,
                state = imageState,
                modifier = imageModifier,
                contentScale = ContentScale.Fit,
            )
        }
    },
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        when (mediaType) {
            MediaCapability.IMAGE -> imageContent(
                localUri,
                contentDescription,
                Modifier.fillMaxSize(),
            )

            MediaCapability.VIDEO -> videoContent(
                localUri,
                contentDescription,
                Modifier.fillMaxSize(),
            )

            MediaCapability.MUSIC -> audioContent(
                localUri,
                contentDescription,
                Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(name = "Fullscreen media light", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FullscreenMediaViewerLightPreview() {
    PocketCrewTheme(darkTheme = false) {
        FullscreenMediaViewerPreviewContent()
    }
}

@Preview(name = "Fullscreen media dark", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FullscreenMediaViewerDarkPreview() {
    PocketCrewTheme(darkTheme = true) {
        FullscreenMediaViewerPreviewContent()
    }
}

@Composable
private fun FullscreenMediaViewerPreviewContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        ) {
            FullscreenMediaViewer(
                localUri = "file:///preview-image.png",
                mediaType = MediaCapability.IMAGE,
                contentDescription = "Preview image",
                videoContent = { _, _, _ -> Unit },
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        ) {
            FullscreenMediaViewer(
                localUri = "file:///preview-video.mp4",
                mediaType = MediaCapability.VIDEO,
                contentDescription = "Preview video",
                videoContent = { _, description, modifier ->
                    FullscreenMediaPreviewPlaceholder(
                        contentDescription = description,
                        icon = Icons.Default.Movie,
                        modifier = modifier,
                    )
                },
            )
        }
    }
}

@Composable
private fun FullscreenMediaPreviewPlaceholder(
    contentDescription: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.DarkGray)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(56.dp),
        )
    }
}
