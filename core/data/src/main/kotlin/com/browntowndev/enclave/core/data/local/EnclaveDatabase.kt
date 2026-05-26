package com.browntowndev.enclave.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ChatEntity::class,
        MessageEntity::class,
        MessageTranscriptSegmentEntity::class,
        ChatSummaryEntity::class,
        LocalModelEntity::class,
        LocalModelConfigurationEntity::class,
        ApiModelConfigurationEntity::class,
        ApiCredentialsEntity::class,
        DefaultModelEntity::class,
        WebSearchSourceEntity::class,
        MessageVisionAnalysisEntity::class,
        EmbeddingEntity::class,
        TtsProviderEntity::class,
        MemoriesEntity::class,
        MediaProviderEntity::class,
        StudioMediaEntity::class,
        StudioAlbumEntity::class,
        MessageFileEntity::class,
        DocumentEntity::class,
        UtilityModelEntity::class,
        MessageInferenceTelemetryEntity::class,
        MessageCommittedInferencePromptEntity::class,
    ],
    version = 4,
)
@TypeConverters(ModelTypeConverters::class)
abstract class EnclaveDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    abstract fun messageQueryDao(): com.browntowndev.enclave.core.data.local.dao.message.MessageQueryDao

    abstract fun messageCommandDao(): com.browntowndev.enclave.core.data.local.dao.message.MessageCommandDao

    abstract fun messageSyncCommandDao(): com.browntowndev.enclave.core.data.local.dao.message.MessageSyncCommandDao

    abstract fun chatSummaryDao(): ChatSummaryDao

    abstract fun localModelsDao(): LocalModelsDao

    abstract fun localModelConfigurationsDao(): LocalModelConfigurationsDao

    abstract fun apiModelConfigurationsDao(): ApiModelConfigurationsDao

    abstract fun apiCredentialsDao(): ApiCredentialsDao

    abstract fun defaultModelsDao(): DefaultModelsDao

    abstract fun webSearchSourceDao(): WebSearchSourceDao

    abstract fun messageVisionAnalysisDao(): MessageVisionAnalysisDao

    abstract fun embeddingDao(): EmbeddingDao

    abstract fun ttsProviderDao(): TtsProviderDao

    abstract fun memoriesDao(): MemoriesDao

    abstract fun mediaProviderDao(): MediaProviderDao

    abstract fun studioMediaDao(): StudioMediaDao

    abstract fun studioAlbumDao(): StudioAlbumDao

    abstract fun messageFileDao(): MessageFileDao

    abstract fun messageTranscriptSegmentDao(): MessageTranscriptSegmentDao

    abstract fun cleanupDao(): CleanupDao

    abstract fun documentDao(): DocumentDao

    abstract fun utilityModelsDao(): UtilityModelsDao

    abstract fun messageInferenceTelemetryDao(): MessageInferenceTelemetryDao

    abstract fun firestoreSyncPlaceholderDao(): FirestoreSyncPlaceholderDao

    abstract fun messageCommittedInferencePromptDao(): MessageCommittedInferencePromptDao
}
