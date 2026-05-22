package com.browntowndev.enclave.feature.chat.components.messagelist

import androidx.compose.runtime.Immutable
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.feature.chat.ArtifactCardUiModel

@Immutable
internal data class MessageListCallbacks(
    val onEditMessage: (String) -> Unit = {},
    val onPlayAudio: (String) -> Unit = {},
    val onArtifactCardClick: (MessageId, ArtifactCardUiModel) -> Unit = { _, _ -> },
    val onNavigateToStudio: (
        ids: List<String>,
        uris: List<String>,
        types: List<String>,
        prompts: List<String>,
    ) -> Unit = { _, _, _, _ -> },
    val onShareClick: (MessageId) -> Unit = {},
)
