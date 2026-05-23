package com.browntowndev.enclave.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.media.isActiveVoiceInput

@Composable
fun UniversalPromptTextField(
    value: String,
    onValueChange: (String) -> Unit,
    speechState: SpeechState,
    idlePlaceholder: String,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    maxLinesCollapsed: Int = 3,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, top = 4.dp),
) {
    val fieldModifier =
        modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                },
            )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = fieldModifier,
        textStyle =
            MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
            ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        maxLines = if (isExpanded) Int.MAX_VALUE else maxLinesCollapsed,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Default,
            ),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.padding(contentPadding)) {
                if (value.isEmpty() || speechState.isActiveVoiceInput) {
                    UniversalVoicePromptPlaceholder(
                        speechState = speechState,
                        idlePlaceholder = idlePlaceholder,
                    )
                }
                if (!speechState.isActiveVoiceInput) {
                    innerTextField()
                }
            }
        },
    )
}
