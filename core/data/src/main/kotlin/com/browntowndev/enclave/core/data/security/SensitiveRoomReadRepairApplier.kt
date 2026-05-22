package com.browntowndev.enclave.core.data.security

import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatSummaryDao
import com.browntowndev.enclave.core.data.local.DocumentDao
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.MediaProviderDao
import com.browntowndev.enclave.core.data.local.MemoriesDao
import com.browntowndev.enclave.core.data.local.MessageDao
import com.browntowndev.enclave.core.data.local.MessageFileDao
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.enclave.core.data.local.StudioAlbumDao
import com.browntowndev.enclave.core.data.local.StudioMediaDao
import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.local.WebSearchSourceDao
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists ciphertext produced during read-side self-heal ([SensitiveRoomReadRepair]) so plaintext
 * does not remain at rest once the vault is unlocked.
 */
@Singleton
class SensitiveRoomReadRepairApplier
    @Inject
    constructor(
        private val chatDao: ChatDao,
        private val messageDao: MessageDao,
        private val messageFileDao: MessageFileDao,
        private val chatSummaryDao: ChatSummaryDao,
        private val memoriesDao: MemoriesDao,
        private val messageVisionAnalysisDao: MessageVisionAnalysisDao,
        private val webSearchSourceDao: WebSearchSourceDao,
        private val messageTranscriptSegmentDao: MessageTranscriptSegmentDao,
        private val documentDao: DocumentDao,
        private val apiCredentialsDao: ApiCredentialsDao,
        private val apiModelConfigurationsDao: ApiModelConfigurationsDao,
        private val localModelConfigurationsDao: LocalModelConfigurationsDao,
        private val ttsProviderDao: TtsProviderDao,
        private val mediaProviderDao: MediaProviderDao,
        private val studioAlbumDao: StudioAlbumDao,
        private val studioMediaDao: StudioMediaDao,
    ) {
        suspend fun apply(repairs: List<SensitiveRoomReadRepair>) {
            if (repairs.isEmpty()) return
            val grouped =
                repairs.groupBy { repair ->
                    "${repair.ref.owner}\u0000${repair.entityId}"
                }
            for ((_, group) in grouped) {
                val owner = group.first().ref.owner
                val entityId = group.first().entityId
                val fields = group.associate { it.ref.field to it.encryptedValue }
                when (owner) {
                    "ChatEntity" -> applyChat(entityId, fields)
                    "MessageEntity" -> applyMessage(entityId, fields)
                    "MessageFileEntity" -> applyMessageFile(entityId, fields)
                    "ChatSummaryEntity" -> applyChatSummary(entityId, fields)
                    "MemoriesEntity" -> applyMemory(entityId, fields)
                    "MessageVisionAnalysisEntity" -> applyVisionAnalysis(entityId, fields)
                    "WebSearchSourceEntity" -> applyWebSearchSource(entityId, fields)
                    "MessageTranscriptSegmentEntity" -> applyTranscriptSegment(entityId, fields)
                    "DocumentEntity" -> applyDocument(entityId, fields)
                    "ApiCredentialsEntity" -> applyApiCredentials(entityId, fields)
                    "ApiModelConfigurationEntity" -> applyApiModelConfiguration(entityId, fields)
                    "LocalModelConfigurationEntity" -> applyLocalModelConfiguration(entityId, fields)
                    "TtsProviderEntity" -> applyTtsProvider(entityId, fields)
                    "MediaProviderEntity" -> applyMediaProvider(entityId, fields)
                    "StudioAlbumEntity" -> applyStudioAlbum(entityId, fields)
                    "StudioMediaEntity" -> applyStudioMedia(entityId, fields)
                }
            }
        }

        private suspend fun applyChat(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val chat = chatDao.getChatById(ChatId(entityId)) ?: return
            val name = fields["name"] ?: return
            val ts = System.currentTimeMillis()
            chatDao.update(chat.copy(name = name, pendingSync = 1, updatedAt = ts))
        }

        private suspend fun applyMessage(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val row = messageDao.getMessageById(MessageId(entityId)) ?: return
            val ts = System.currentTimeMillis()
            var m = row.message
            for ((field, enc) in fields) {
                m =
                    when (field) {
                        "content" -> m.copy(content = enc, pendingSync = 1, updatedAt = ts)
                        "imageUrisJson" -> m.copy(imageUrisJson = enc, pendingSync = 1, updatedAt = ts)
                        "thinkingRaw" -> m.copy(thinkingRaw = enc, pendingSync = 1, updatedAt = ts)
                        "artifactsJson" -> m.copy(artifactsJson = enc, pendingSync = 1, updatedAt = ts)
                        "generatedMediaUrisJson" -> m.copy(generatedMediaUrisJson = enc, pendingSync = 1, updatedAt = ts)
                        "generatedMediaAssetIdsJson" -> m.copy(generatedMediaAssetIdsJson = enc, pendingSync = 1, updatedAt = ts)
                        "generatedMediaPromptsJson" -> m.copy(generatedMediaPromptsJson = enc, pendingSync = 1, updatedAt = ts)
                        else -> m
                    }
            }
            messageDao.update(m)
        }

        private suspend fun applyMessageFile(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val file = messageFileDao.getById(entityId) ?: return
            val ts = System.currentTimeMillis()
            var f = file
            for ((field, enc) in fields) {
                f =
                    when (field) {
                        "name" -> f.copy(name = enc, pendingSync = 1, updatedAt = ts)
                        "content" -> f.copy(content = enc, pendingSync = 1, updatedAt = ts)
                        "remoteUrl" -> f.copy(remoteUrl = enc, pendingSync = 1, updatedAt = ts)
                        "stagingRemoteUrl" -> f.copy(stagingRemoteUrl = enc, pendingSync = 1, updatedAt = ts)
                        else -> f
                    }
            }
            messageFileDao.insert(f)
        }

        private suspend fun applyChatSummary(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val summary = chatSummaryDao.getSummaryForChatSync(ChatId(entityId)) ?: return
            val content = fields["content"] ?: return
            val ts = System.currentTimeMillis()
            chatSummaryDao.insertOrUpdateSummary(summary.copy(content = content, pendingSync = 1, updatedAt = ts))
        }

        private suspend fun applyMemory(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val memory = memoriesDao.getMemoryById(entityId) ?: return
            val content = fields["content"] ?: return
            val ts = System.currentTimeMillis()
            memoriesDao.updateMemory(memory.copy(content = content, pendingSync = 1, updatedAt = ts))
        }

        private suspend fun applyVisionAnalysis(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val row = messageVisionAnalysisDao.getById(entityId) ?: return
            val ts = System.currentTimeMillis()
            var v = row
            for ((field, enc) in fields) {
                v =
                    when (field) {
                        "imageUri" -> v.copy(imageUri = enc, pendingSync = 1, updatedAt = ts)
                        "promptText" -> v.copy(promptText = enc, pendingSync = 1, updatedAt = ts)
                        "analysisText" -> v.copy(analysisText = enc, pendingSync = 1, updatedAt = ts)
                        else -> v
                    }
            }
            messageVisionAnalysisDao.upsert(v)
        }

        private suspend fun applyWebSearchSource(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val source = webSearchSourceDao.getById(entityId) ?: return
            val ts = System.currentTimeMillis()
            var s = source
            for ((field, enc) in fields) {
                s =
                    when (field) {
                        "title" -> s.copy(title = enc, pendingSync = 1, updatedAt = ts)
                        "url" -> s.copy(url = enc, pendingSync = 1, updatedAt = ts)
                        "content" -> s.copy(content = enc, pendingSync = 1, updatedAt = ts)
                        else -> s
                    }
            }
            webSearchSourceDao.upsert(s)
        }

        private suspend fun applyTranscriptSegment(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val seg = messageTranscriptSegmentDao.getById(entityId) ?: return
            val text = fields["text"] ?: return
            val ts = System.currentTimeMillis()
            messageTranscriptSegmentDao.upsert(seg.copy(text = text, pendingSync = 1, updatedAt = ts))
        }

        private suspend fun applyDocument(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val doc = documentDao.getDocumentById(entityId) ?: return
            val ts = System.currentTimeMillis()
            var d = doc
            for ((field, enc) in fields) {
                d =
                    when (field) {
                        "json" -> d.copy(json = enc, pendingSync = 1, updatedAt = ts)
                        "title" -> d.copy(title = enc, pendingSync = 1, updatedAt = ts)
                        "author" -> d.copy(author = enc, pendingSync = 1, updatedAt = ts)
                        else -> d
                    }
            }
            documentDao.updateDocument(d)
        }

        private suspend fun applyApiCredentials(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val row = apiCredentialsDao.getById(ApiCredentialsId(entityId)) ?: return
            val ts = System.currentTimeMillis()
            var e = row
            for ((field, enc) in fields) {
                e =
                    when (field) {
                        "displayName" -> e.copy(displayName = enc, pendingSync = 1, updatedAt = ts)
                        "baseUrl" -> e.copy(baseUrl = enc, pendingSync = 1, updatedAt = ts)
                        else -> e
                    }
            }
            apiCredentialsDao.update(e)
        }

        private suspend fun applyApiModelConfiguration(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val row = apiModelConfigurationsDao.getById(ApiModelConfigurationId(entityId)) ?: return
            val ts = System.currentTimeMillis()
            var e = row
            for ((field, enc) in fields) {
                e =
                    when (field) {
                        "displayName" -> e.copy(displayName = enc, pendingSync = 1, updatedAt = ts)
                        "customHeaders" -> e.copy(customHeaders = enc, pendingSync = 1, updatedAt = ts)
                        "systemPrompt" -> e.copy(systemPrompt = enc, pendingSync = 1, updatedAt = ts)
                        else -> e
                    }
            }
            apiModelConfigurationsDao.upsert(e)
        }

        private suspend fun applyLocalModelConfiguration(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val row = localModelConfigurationsDao.getById(LocalModelConfigurationId(entityId)) ?: return
            val ts = System.currentTimeMillis()
            var e = row
            for ((field, enc) in fields) {
                e =
                    when (field) {
                        "displayName" -> e.copy(displayName = enc, pendingSync = 1, updatedAt = ts)
                        "systemPrompt" -> e.copy(systemPrompt = enc, pendingSync = 1, updatedAt = ts)
                        else -> e
                    }
            }
            localModelConfigurationsDao.upsert(e)
        }

        private suspend fun applyTtsProvider(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val row = ttsProviderDao.getTtsProvider(entityId) ?: return
            val ts = System.currentTimeMillis()
            var e = row
            for ((field, enc) in fields) {
                e =
                    when (field) {
                        "displayName" -> e.copy(displayName = enc, pendingSync = 1, updatedAt = ts)
                        "voiceName" -> e.copy(voiceName = enc, pendingSync = 1, updatedAt = ts)
                        "modelName" -> e.copy(modelName = enc, pendingSync = 1, updatedAt = ts)
                        "baseUrl" -> e.copy(baseUrl = enc, pendingSync = 1, updatedAt = ts)
                        else -> e
                    }
            }
            ttsProviderDao.upsertTtsProvider(e)
        }

        private suspend fun applyMediaProvider(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val row = mediaProviderDao.getMediaProvider(entityId) ?: return
            val ts = System.currentTimeMillis()
            var e = row
            for ((field, enc) in fields) {
                e =
                    when (field) {
                        "displayName" -> e.copy(displayName = enc, pendingSync = 1, updatedAt = ts)
                        "modelName" -> e.copy(modelName = enc, pendingSync = 1, updatedAt = ts)
                        "baseUrl" -> e.copy(baseUrl = enc, pendingSync = 1, updatedAt = ts)
                        else -> e
                    }
            }
            mediaProviderDao.upsert(e)
        }

        private suspend fun applyStudioAlbum(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val name = fields["name"] ?: return
            studioAlbumDao.updateAlbumName(entityId, name)
        }

        private suspend fun applyStudioMedia(
            entityId: String,
            fields: Map<String, String>,
        ) {
            val row = studioMediaDao.getMediaById(entityId) ?: return
            val ts = System.currentTimeMillis()
            var e = row
            for ((field, enc) in fields) {
                e =
                    when (field) {
                        "prompt" -> e.copy(prompt = enc, pendingSync = 1, updatedAt = ts)
                        "mediaUri" -> e.copy(mediaUri = enc, pendingSync = 1, updatedAt = ts)
                        "remoteUrl" -> e.copy(remoteUrl = enc, pendingSync = 1, updatedAt = ts)
                        "remoteThumbnailUrl" -> e.copy(remoteThumbnailUrl = enc, pendingSync = 1, updatedAt = ts)
                        "stagingRemoteUrl" -> e.copy(stagingRemoteUrl = enc, pendingSync = 1, updatedAt = ts)
                        else -> e
                    }
            }
            studioMediaDao.insertMedia(e)
        }
    }
