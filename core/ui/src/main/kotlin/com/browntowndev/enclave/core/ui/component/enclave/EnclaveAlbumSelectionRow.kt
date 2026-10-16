package com.browntowndev.enclave.core.ui.component.enclave

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.browntowndev.enclave.core.ui.component.VideoThumbnail
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.domain.model.config.MediaCapability

@Composable
fun EnclaveAlbumSelectionRow(
    name: String,
    itemCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    coverUri: String? = null,
    coverMediaType: MediaCapability? = null,
) {
    Row(
        modifier =
            modifier
                .enclaveSurface(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    borderColor = enclaveBorderColor(),
                    withShadow = false,
                ).clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EnclaveAlbumThumbnail(
            coverUri = coverUri,
            coverMediaType = coverMediaType,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$itemCount items",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EnclaveAlbumThumbnail(
    coverUri: String?,
    coverMediaType: MediaCapability?,
    modifier: Modifier = Modifier,
) {
    val thumbnailModifier =
        modifier.size(EnclaveNeoTokens.albumThumbnailSize)

    if (coverUri != null) {
        Box(
            modifier =
                thumbnailModifier.enclaveSurface(
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    borderColor = enclaveBorderColor(),
                    withShadow = false,
                ),
        ) {
            val mediaModifier = Modifier.fillMaxSize()
            if (coverMediaType == MediaCapability.VIDEO) {
                VideoThumbnail(
                    videoUri = coverUri,
                    contentDescription = null,
                    modifier = mediaModifier,
                )
            } else {
                AsyncImage(
                    model = coverUri,
                    contentDescription = null,
                    modifier = mediaModifier,
                    contentScale = ContentScale.Crop,
                )
            }
        }
    } else {
        Box(
            modifier =
                thumbnailModifier.enclaveSurface(
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                    borderColor = enclaveBorderColor(),
                    withShadow = false,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
