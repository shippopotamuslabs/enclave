package com.browntowndev.pocketcrew.presentation.screen.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.pocketcrew.R
import com.browntowndev.pocketcrew.presentation.screen.chat.Mode
import com.browntowndev.pocketcrew.presentation.theme.PocketCrewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputBar(
    inputText: String,
    selectedMode: Mode,
    isExpanded: Boolean,
    onInputChange: (String) -> Unit,
    onModeChange: (Mode) -> Unit,
    onSend: (String) -> Unit,
    onExpandToggle: () -> Unit,
    onAttach: () -> Unit,
    modifier: Modifier = Modifier
) {
    var modeExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    var textFieldValue by remember { mutableStateOf(TextFieldValue(inputText)) }

    LaunchedEffect(inputText) {
        if (textFieldValue.text != inputText) {
            textFieldValue = TextFieldValue(inputText)
        }
    }

    val maxLines = if (isExpanded) 5 else 1
    val currentLineCount = textFieldValue.text.lines().size.coerceAtLeast(1)
    val showExpandIcon = isExpanded && currentLineCount >= 3
    val imeAction = if (maxLines == 1) ImeAction.Send else ImeAction.Default

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize()
            .clickable { focusRequester.requestFocus() },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ── Expandable Text Area (full width, top-aligned) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp)   // ← restored exactly to your preferred 16.dp
            ) {
                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        onInputChange(newValue.text)
                    },
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = maxLines,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = imeAction
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { if (textFieldValue.text.isNotBlank()) onSend(textFieldValue.text) }
                    ),
                    decorationBox = { innerTextField ->
                        Box {
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
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                // Expand/collapse icon — tucked deep into the top-right corner
                if (showExpandIcon) {
                    IconButton(
                        onClick = onExpandToggle,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 0.dp, end = 0.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.collapse_content),
                            contentDescription = "Collapse input",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                IconButton(onClick = onAttach) {
                    Icon(
                        painter = painterResource(R.drawable.attach_file),
                        contentDescription = "Attach file",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.weight(1f))

                // Mode selector
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
                        Mode.entries.forEach { mode ->
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

                // Send
                IconButton(
                    onClick = { onSend(textFieldValue.text) },
                    enabled = textFieldValue.text.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        tint = if (textFieldValue.text.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
            selectedMode = Mode.AUTO,
            isExpanded = false,
            onInputChange = {},
            onModeChange = {},
            onSend = {},
            onExpandToggle = {},
            onAttach = {}
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
            selectedMode = Mode.CREW,
            isExpanded = true,
            onInputChange = {},
            onModeChange = {},
            onSend = {},
            onExpandToggle = {},
            onAttach = {}
        )
    }
}

@Preview
@Composable
fun PreviewInputBarSingleLine() {
    PocketCrewTheme {
        InputBar(
            inputText = "Single line message",
            selectedMode = Mode.FAST,
            isExpanded = false,
            onInputChange = {},
            onModeChange = {},
            onSend = {},
            onExpandToggle = {},
            onAttach = {}
        )
    }
}
