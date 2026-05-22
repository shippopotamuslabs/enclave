package com.browntowndev.enclave.core.data.local.dao.message

import com.browntowndev.enclave.core.data.local.WebSearchSourceEntity
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineStep

data class MessagePersistPayload(
    val messageId: MessageId,
    val modelType: ModelType,
    val thinkingStartTime: Long,
    val thinkingEndTime: Long,
    val thinkingDuration: Int?,
    val thinkingRaw: String?,
    val content: String,
    val messageState: MessageState,
    val pipelineStep: PipelineStep?,
    val webSearchSources: List<WebSearchSourceEntity> = emptyList(),
    val mediaUrisJson: String? = null,
    val mediaAssetIdsJson: String? = null,
    val mediaTypesJson: String? = null,
    val mediaPromptsJson: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
