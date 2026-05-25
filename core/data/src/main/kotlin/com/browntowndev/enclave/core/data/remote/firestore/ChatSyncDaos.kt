package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatSummaryDao
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.local.MessageFileDao
import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryDao
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.enclave.core.data.local.FirestoreSyncPlaceholderDao
import com.browntowndev.enclave.core.data.local.WebSearchSourceDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatSyncDaos
    @Inject
    constructor(
        val chatDao: ChatDao,
        val messageDao: MessageDao,
        val chatSummaryDao: ChatSummaryDao,
        val messageFileDao: MessageFileDao,
        val messageVisionAnalysisDao: MessageVisionAnalysisDao,
        val messageTranscriptSegmentDao: MessageTranscriptSegmentDao,
        val messageInferenceTelemetryDao: MessageInferenceTelemetryDao,
        val webSearchSourceDao: WebSearchSourceDao,
        val documentDao: DocumentDao,
        val embeddingDao: EmbeddingDao,
        val firestoreSyncPlaceholderDao: FirestoreSyncPlaceholderDao,
    )
