package com.browntowndev.enclave.feature.chat.components.assistantresponse

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.browntowndev.enclave.core.ui.theme.LlmBodyTextStyle

/** Slightly larger body for assistant markdown in chat (default [LlmBodyTextStyle] is 16/26). */
internal val AssistantResponseBodyTextStyle: TextStyle =
    LlmBodyTextStyle.copy(
        fontSize = 18.sp,
        lineHeight = 29.sp,
    )
