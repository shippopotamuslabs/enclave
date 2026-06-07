package com.browntowndev.enclave.core.data.remote.firestore

internal class FirestoreSyncDaos private constructor(
    chatSyncDaos: ChatSyncDaos,
    configSyncDaos: ConfigSyncDaos,
    studioSyncDaos: StudioSyncDaos,
) {
    val chatDao = chatSyncDaos.chatDao
    val messageDao = chatSyncDaos.messageDao
    val messageTranscriptSegmentDao = chatSyncDaos.messageTranscriptSegmentDao
    val messageInferenceTelemetryDao = chatSyncDaos.messageInferenceTelemetryDao
    val memoriesDao = configSyncDaos.memoriesDao
    val webSearchSourceDao = chatSyncDaos.webSearchSourceDao
    val ttsProviderDao = configSyncDaos.ttsProviderDao
    val liveVoiceProviderDao = configSyncDaos.liveVoiceProviderDao
    val mediaProviderDao = configSyncDaos.mediaProviderDao
    val apiModelConfigurationsDao = configSyncDaos.apiModelConfigurationsDao
    val localModelsDao = configSyncDaos.localModelsDao
    val apiCredentialsDao = configSyncDaos.apiCredentialsDao
    val localModelConfigurationsDao = configSyncDaos.localModelConfigurationsDao
    val defaultModelsDao = configSyncDaos.defaultModelsDao
    val utilityModelsDao = configSyncDaos.utilityModelsDao
    val studioMediaDao = studioSyncDaos.studioMediaDao
    val studioAlbumDao = studioSyncDaos.studioAlbumDao
    val messageFileDao = chatSyncDaos.messageFileDao
    val messageVisionAnalysisDao = chatSyncDaos.messageVisionAnalysisDao
    val chatSummaryDao = chatSyncDaos.chatSummaryDao
    val documentDao = chatSyncDaos.documentDao
    val embeddingDao = chatSyncDaos.embeddingDao
    val firestoreSyncPlaceholderDao = chatSyncDaos.firestoreSyncPlaceholderDao

    companion object {
        fun from(
            chatSyncDaos: ChatSyncDaos,
            configSyncDaos: ConfigSyncDaos,
            studioSyncDaos: StudioSyncDaos,
        ): FirestoreSyncDaos = FirestoreSyncDaos(chatSyncDaos, configSyncDaos, studioSyncDaos)
    }
}
