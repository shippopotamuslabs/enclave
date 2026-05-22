package com.browntowndev.enclave.feature.chat.components.messagebubble

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import com.browntowndev.enclave.core.ui.component.neo.neoBorderColor
import com.browntowndev.enclave.core.ui.component.neo.neoShadowColor
import com.browntowndev.enclave.core.ui.component.neo.neoSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.FileAttachment
import com.browntowndev.enclave.feature.chat.R
import kotlinx.coroutines.launch

private const val USER_BUBBLE_WIDTH_FRACTION = 0.85f

@Composable
internal fun UserBubble(
    modifier: Modifier = Modifier,
    message: ChatMessage,
    onEditClick: (String) -> Unit = {},
) {
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var showActions by remember { mutableStateOf(false) }
    var fullscreenImageUri by remember { mutableStateOf<String?>(null) }

    val copyToClipboard: (String) -> Unit = { content ->
        coroutineScope.launch {
            clipboardManager.setClipEntry(
                ClipEntry(plainTextClipData(content)),
            )
        }
    }

    fullscreenImageUri?.let { imageUri ->
        FullscreenImageDialog(
            imageUri = imageUri,
            onDismiss = { fullscreenImageUri = null },
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
        ) {
            UserBubbleImagePreviews(
                imageUris = message.content.imageUris,
                onImageClick = { fullscreenImageUri = it },
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction = USER_BUBBLE_WIDTH_FRACTION)
                        .padding(
                            end = 2.dp,
                            bottom = EnclaveNeoTokens.userBubbleShadowOffset,
                        )
                        .zIndex(1f)
                        .neoSurface(
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            borderColor = neoBorderColor(),
                            withShadow = true,
                            shadowColor = neoShadowColor(),
                            shadowOffset = EnclaveNeoTokens.userBubbleShadowOffset,
                            shape = EnclaveNeoTokens.userBubbleShape,
                        )
                        .clickable(
                            onClickLabel = if (showActions) "Hide message actions" else "Show message actions",
                        ) { showActions = !showActions },
            ) {
                Column(modifier = Modifier.padding(EnclaveNeoTokens.userBubbleContentPadding)) {
                    val bubbleContentColor = MaterialTheme.colorScheme.onSurface
                    UserBubbleFileAttachments(attachments = message.content.fileAttachments)

                    SelectionContainer(modifier = Modifier.fillMaxWidth()) {
                        CompositionLocalProvider(LocalContentColor provides bubbleContentColor) {
                            UserBubbleMessageBody(
                                contentText = message.content.text,
                                contentColor = bubbleContentColor,
                                hasImages = message.content.imageUris.isNotEmpty(),
                                onCopyCode = copyToClipboard,
                            )
                        }
                    }
                }
            }

            Text(
                text = message.formattedTimestamp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier =
                    Modifier
                        .fillMaxWidth(fraction = USER_BUBBLE_WIDTH_FRACTION)
                        .padding(top = 4.dp, end = 2.dp),
            )

            BubbleActions(
                visible = showActions,
                onEditClick = {
                    onEditClick(message.content.text)
                    showActions = false
                },
                onCopyClick = {
                    copyToClipboard(message.content.text)
                    showActions = false
                },
            )
        }
    }
}

@Composable
private fun UserBubbleImagePreviews(
    imageUris: List<String>,
    onImageClick: (String) -> Unit,
) {
    if (imageUris.isEmpty()) return

    Row(
        modifier =
            Modifier
                .padding(bottom = 4.dp)
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        imageUris.forEach { imageUri ->
            AsyncImage(
                model = imageUri,
                contentDescription = "Attached image",
                modifier =
                    Modifier
                        .widthIn(max = 120.dp)
                        .heightIn(max = 120.dp)
                        .clip(EnclaveNeoTokens.shape)
                        .clickable { onImageClick(imageUri) },
            )
        }
    }
}

@Composable
private fun UserBubbleFileAttachments(
    attachments: List<FileAttachment>,
) {
    if (attachments.isEmpty()) return

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            AssistChip(
                onClick = { /* Open viewer potentially */ },
                label = {
                    Text(
                        text = attachment.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.document),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                colors =
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                    ),
                border = null,
            )
        }
    }
}

@Composable
private fun UserBubbleMessageBody(
    contentText: String,
    contentColor: Color,
    hasImages: Boolean,
    onCopyCode: (String) -> Unit,
) {
    if (contentText.isBlank()) {
        if (!hasImages) {
            Text(
                text = "",
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
        }
        return
    }

    if (contentText.contains("```")) {
        val codeContent =
            contentText
                .substringAfter("```")
                .substringBeforeLast("```")
                .trim()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
        ) {
            Text(
                text = codeContent,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(end = 8.dp),
            )
            IconButton(onClick = { onCopyCode(codeContent) }) {
                Icon(
                    painter = painterResource(R.drawable.content_copy),
                    contentDescription = "Copy code",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        ExpandableTruncatableMessageText(
            text = contentText,
            color = contentColor,
        )
    }
}

@Composable
private fun FullscreenImageDialog(
    imageUri: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .systemBarsPadding(),
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = "Fullscreen preview",
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                contentScale = ContentScale.Fit,
            )
            IconButton(
                onClick = onDismiss,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close preview",
                    tint = Color.White,
                )
            }
        }
    }
}
