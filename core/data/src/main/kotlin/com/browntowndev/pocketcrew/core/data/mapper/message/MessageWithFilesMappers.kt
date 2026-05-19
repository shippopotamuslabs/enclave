package com.browntowndev.pocketcrew.core.data.mapper.message

import com.browntowndev.pocketcrew.core.data.local.DocumentEntity
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceEntity
import com.browntowndev.pocketcrew.core.data.local.dao.message.MessageWithFiles
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageTranscriptSegment

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
