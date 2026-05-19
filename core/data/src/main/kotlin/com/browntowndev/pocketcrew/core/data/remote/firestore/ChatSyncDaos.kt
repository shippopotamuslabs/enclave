package com.browntowndev.pocketcrew.core.data.remote.firestore

import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.ChatSummaryDao
import com.browntowndev.pocketcrew.core.data.local.DocumentDao
import com.browntowndev.pocketcrew.core.data.local.EmbeddingDao
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageFileDao
import com.browntowndev.pocketcrew.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceDao
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
        val webSearchSourceDao: WebSearchSourceDao,
        val documentDao: DocumentDao,
        val embeddingDao: EmbeddingDao,
    )
