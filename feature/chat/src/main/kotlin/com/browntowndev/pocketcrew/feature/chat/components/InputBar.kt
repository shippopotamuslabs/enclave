package com.browntowndev.pocketcrew.feature.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.browntowndev.pocketcrew.feature.chat.R
import com.browntowndev.pocketcrew.feature.chat.ChatModeUi
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputBar(
    inputText: String,
    selectedImageUri: String?,
    isPhotoAttachmentEnabled: Boolean,
    photoAttachmentDisabledReason: String?,
    selectedMode: ChatModeUi,
    isGenerating: Boolean,
    isGlobalInferenceBlocked: Boolean = false,
    onInputChange: (String) -> Unit,
    onModeChange: (ChatModeUi) -> Unit,
    onSend: (String) -> Unit,
    onStopGenerating: () -> Unit,
    onAttach: () -> Unit,
    onClearAttachment: () -> Unit,
    modifier: Modifier = Modifier
) {
    var modeExpanded by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    var textFieldValue by remember { mutableStateOf(TextFieldValue(inputText)) }

    LaunchedEffect(inputText) {
        if (textFieldValue.text != inputText) {
            textFieldValue = TextFieldValue(inputText)
        }
    }

    // Auto-expand: when text exceeds ~60 chars OR has newlines, show expand icon and auto-expand
    val hasNewline = textFieldValue.text.contains('\n')
    val isLongText = textFieldValue.text.length > 60
    val showExpandIcon = hasNewline || isLongText

    // Request focus when expanded
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            focusManager.clearFocus()
            focusRequester.requestFocus()
        }
    }

    // When expanded: unlimited. When collapsed: auto-grow up to 5 lines
    val maxLines = if (isExpanded) Int.MAX_VALUE else 5
    // Always use Enter key - send button handles sending, prevents accidental sends during thinking
    val imeAction = ImeAction.Default

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isExpanded) Modifier.fillMaxHeight(0.9f) else Modifier.heightIn(min = 56.dp))
            .animateContentSize()
            .then(if (!isExpanded) Modifier.clickable { focusRequester.requestFocus() } else Modifier),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isExpanded) Modifier.fillMaxHeight() else Modifier)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = when {
                isExpanded -> Arrangement.SpaceBetween
                else -> Arrangement.spacedBy(6.dp)
            }
        ) {
            if (selectedImageUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                ) {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = "Selected image preview",
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    IconButton(
                        onClick = onClearAttachment,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove selected image",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Expanded: use a Box wrapper that fills remaining space and handles clicks
            if (isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            focusManager.clearFocus()
                            focusRequester.requestFocus()
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Text field
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = { newValue ->
                                textFieldValue = newValue
                                onInputChange(newValue.text)
                            },
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 16.sp,
                                lineHeight = 22.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = maxLines,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = imeAction
                            ),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.padding(start = 16.dp, top = 10.dp)) {
                                    if (textFieldValue.text.isEmpty()) {
                                        Text(
                                            text = "Prompt the Pocket Crew",
                                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                        )

                        // Collapse icon
                        IconButton(onClick = {
                            isExpanded = !isExpanded
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.collapse_content),
                                contentDescription = "Collapse input",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Collapsed: simple Row layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    // Text field
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            textFieldValue = newValue
                            onInputChange(newValue.text)
                        },
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 16.sp,
                            lineHeight = 22.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = maxLines,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = imeAction
                        ),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.padding(start = 16.dp, top = 10.dp)) {
                                if (textFieldValue.text.isEmpty()) {
                                    Text(
                                        text = "Prompt the Pocket Crew",
                                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                    )

                    // Expand icon (if applicable)
                    if (showExpandIcon) {
                        IconButton(onClick = {
                            isExpanded = !isExpanded
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.expand_content),
                                contentDescription = "Expand input",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                }
            }

            // ── Fixed bottom action row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Attachment (left-aligned)
                IconButton(
                    onClick = onAttach,
                    enabled = isPhotoAttachmentEnabled,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.attach_file),
                        contentDescription = "Attach file",
                        tint = if (isPhotoAttachmentEnabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        }
                    )
                }

                Spacer(Modifier.weight(1f))

                // ChatModeUi selector
                ExposedDropdownMenuBox(
                    expanded = modeExpanded,
                    onExpandedChange = { modeExpanded = it }
                ) {
                    // IconButton is here because it visually mimics the icon being clicked
                    IconButton(
                        onClick = { /* Toggle handled via onExpandedChange */ },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    ) {

                        Icon(
                            painter = painterResource(selectedMode.iconRes),
                            contentDescription = selectedMode.getDisplayName(context),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ExposedDropdownMenu(
                        expanded = modeExpanded,
                        onDismissRequest = { modeExpanded = false },
                        modifier = Modifier.width(200.dp)
                    ) {
                        ChatModeUi.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.getDisplayName(context)) },
                                onClick = {
                                    onModeChange(mode)
                                    modeExpanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(mode.iconRes),
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }

                // Send / Stop
                val hasSendableContent = textFieldValue.text.isNotBlank() || selectedImageUri != null
                val isSendDisabled = (isGenerating || isGlobalInferenceBlocked) && !isGenerating
                if (isGenerating) {
                    FilledIconButton(
                        onClick = onStopGenerating,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop generating",
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (hasSendableContent && !isSendDisabled) {
                                val textToSend = textFieldValue.text
                                onSend(textToSend) // Send FIRST while inputText still has value
                                textFieldValue = TextFieldValue("") // Clear locally
                                onInputChange("") // Clear parent state
                                isExpanded = false
                                focusManager.clearFocus()
                            }
                        },
                        enabled = hasSendableContent && !isSendDisabled
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = when {
                                isSendDisabled -> MaterialTheme.colorScheme.onSurfaceVariant
                                hasSendableContent -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            if (!isPhotoAttachmentEnabled && !photoAttachmentDisabledReason.isNullOrBlank()) {
                Text(
                    text = photoAttachmentDisabledReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                )
            }
        }
    }
}

// ==================== PREVIEWS ====================

@Preview
@Composable
fun PreviewInputBar() {
    PocketCrewTheme {
        InputBar(
            inputText = "",
            selectedImageUri = null,
            isPhotoAttachmentEnabled = true,
            photoAttachmentDisabledReason = null,
            selectedMode = ChatModeUi.FAST,
            isGenerating = false,
            onInputChange = {},
            onModeChange = {},
            onSend = {},
            onStopGenerating = {},
            onAttach = {},
            onClearAttachment = {},
        )
    }
}

@Preview
@Composable
fun PreviewInputBarExpanded() {
    PocketCrewTheme {
        InputBar(
            inputText = """
                Line 1 of expanded input.
                Line 2.
                Line 3: showing collapse icon.
            """.trimIndent(),
            selectedImageUri = null,
            isPhotoAttachmentEnabled = false,
            photoAttachmentDisabledReason = "Crew mode requires an API vision model.",
            selectedMode = ChatModeUi.CREW,
            isGenerating = false,
            onInputChange = {},
            onModeChange = {},
            onSend = {},
            onStopGenerating = {},
            onAttach = {},
            onClearAttachment = {},
        )
    }
}

@Preview
@Composable
fun PreviewInputBarSingleLine() {
    PocketCrewTheme {
        InputBar(
            inputText = "Single line message",
            selectedImageUri = null,
            isPhotoAttachmentEnabled = true,
            photoAttachmentDisabledReason = null,
            selectedMode = ChatModeUi.FAST,
            isGenerating = false,
            onInputChange = {},
            onModeChange = {},
            onSend = {},
            onStopGenerating = {},
            onAttach = {},
            onClearAttachment = {},
        )
    }
}

@Preview
@Composable
fun PreviewInputBarThinking() {
    PocketCrewTheme {
        InputBar(
            inputText = "Message while thinking",
            selectedImageUri = null,
            isPhotoAttachmentEnabled = true,
            photoAttachmentDisabledReason = null,
            selectedMode = ChatModeUi.FAST,
            isGenerating = true,
            onInputChange = {},
            onModeChange = {},
            onSend = {},
            onStopGenerating = {},
            onAttach = {},
            onClearAttachment = {},
        )
    }
}

@Preview
@Composable
fun PreviewInputBarThinkingMode() {
    PocketCrewTheme {
        InputBar(
            inputText = "Tell me about quantum physics",
            selectedImageUri = null,
            isPhotoAttachmentEnabled = true,
            photoAttachmentDisabledReason = null,
            selectedMode = ChatModeUi.THINKING,
            isGenerating = false,
            onInputChange = {},
            onModeChange = {},
            onSend = {},
            onStopGenerating = {},
            onAttach = {},
            onClearAttachment = {},
        )
    }
}

@Preview
@Composable
fun PreviewInputBarStopIndicator() {
    PocketCrewTheme {
        InputBar(
            inputText = "Generating response...",
            selectedImageUri = null,
            isPhotoAttachmentEnabled = true,
            photoAttachmentDisabledReason = null,
            selectedMode = ChatModeUi.FAST,
            isGenerating = true,
            onInputChange = {},
            onModeChange = {},
            onSend = {},
            onStopGenerating = {},
            onAttach = {},
            onClearAttachment = {},
        )
    }
}
