package com.browntowndev.enclave.core.data.mapper.message

import com.browntowndev.enclave.core.data.local.DocumentEntity
import com.browntowndev.enclave.core.data.local.WebSearchSourceEntity
import com.browntowndev.enclave.core.data.local.dao.message.MessageWithFiles
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment

fun MessageWithFiles.toDomain(
    webSearchSources: List<WebSearchSourceEntity> = emptyList(),
    documents: List<DocumentEntity> = emptyList(),
    transcriptSegments: List<MessageTranscriptSegment> = emptyList(),
    displayUpdatedAt: Long? = null,
): Message =
    message.toDomain(
        webSearchSources = webSearchSources,
        files = files,
        documents = documents,
        transcriptSegments = transcriptSegments,
        displayUpdatedAt = displayUpdatedAt,
    )
