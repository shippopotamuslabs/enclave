package com.browntowndev.enclave.core.ui.component.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.browntowndev.enclave.core.ui.component.StandardTrailingAction
import com.browntowndev.enclave.core.ui.component.UniversalInputBar
import com.browntowndev.enclave.core.ui.component.UniversalPromptTextField
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.port.media.SpeechState
import kotlinx.coroutines.delay

const val CUSTOM_ANIMATION_PROMPT_PANE_TAG = "custom-animation-prompt-pane"
const val CUSTOM_ANIMATION_PROMPT_FIELD_TAG = "custom-animation-prompt-field"

/**
 * Bottom-docked neo input for custom image-to-video prompts on fullscreen media.
 * Matches chat [com.browntowndev.enclave.feature.chat.components.InputBar] behavior:
 * newline on the keyboard, send via the bar action, up to 3 lines before expand chevron.
 */
@Composable
fun CustomAnimationPromptPane(
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var prompt by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val canSend = prompt.isNotBlank()

    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val sendPrompt = {
        if (canSend) {
            onSend(prompt.trim())
        }
    }

    UniversalInputBar(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(WindowInsets.ime.asPaddingValues())
                .testTag(CUSTOM_ANIMATION_PROMPT_PANE_TAG)
                .semantics { contentDescription = "Custom animation prompt" }
                .then(
                    if (!isExpanded) {
                        Modifier.clickable { focusRequester.requestFocus() }
                    } else {
                        Modifier
                    },
                ),
        isExpanded = isExpanded,
        onExpandToggle = { isExpanded = !isExpanded },
        inputContent = {
            UniversalPromptTextField(
                value = prompt,
                onValueChange = { prompt = it },
                speechState = SpeechState.Idle,
                idlePlaceholder = "What should the animation do?",
                isExpanded = isExpanded,
                focusRequester = focusRequester,
                maxLinesCollapsed = 3,
                modifier = Modifier.testTag(CUSTOM_ANIMATION_PROMPT_FIELD_TAG),
            )
        },
        trailingAction = {
            StandardTrailingAction(
                icon = Icons.AutoMirrored.Filled.Send,
                onClick = sendPrompt,
                enabled = canSend,
                description = "Send animation prompt",
            )
        },
    )
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Custom animation prompt dark", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CustomAnimationPromptPaneDarkPreview() {
    EnclaveTheme(darkTheme = true) {
        Box(modifier = Modifier.fillMaxWidth()) {
            CustomAnimationPromptPane(onSend = {})
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(name = "Custom animation prompt light", showBackground = true)
@Composable
private fun CustomAnimationPromptPaneLightPreview() {
    EnclaveTheme(darkTheme = false) {
        Box(modifier = Modifier.fillMaxWidth()) {
            CustomAnimationPromptPane(onSend = {})
        }
    }
}
