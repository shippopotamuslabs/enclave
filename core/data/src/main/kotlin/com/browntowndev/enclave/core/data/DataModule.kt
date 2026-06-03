package com.browntowndev.enclave.core.data

import android.app.NotificationManager
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkManager
import com.browntowndev.enclave.core.data.auth.FirebaseGoogleAuthManager
import com.browntowndev.enclave.core.data.download.DownloadNotificationManager
import com.browntowndev.enclave.core.data.download.DownloadProgressTracker
import com.browntowndev.enclave.core.data.download.DownloadSpeedTracker
import com.browntowndev.enclave.core.data.download.HashingService
import com.browntowndev.enclave.core.data.download.ModelConfigFetcherImpl
import com.browntowndev.enclave.core.data.download.ModelDownloadOrchestratorImpl
import com.browntowndev.enclave.core.data.download.ModelFileScanner
import com.browntowndev.enclave.core.data.download.UtilityModelFileResolver
import com.browntowndev.enclave.core.data.download.remote.DynamicModelUrlProvider
import com.browntowndev.enclave.core.data.download.remote.HttpFileDownloader
import com.browntowndev.enclave.core.data.inference.MiniLMEmbeddingEngine
import com.browntowndev.enclave.core.data.inference.RoomCommittedInferencePromptStore
import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.ChatDao
import com.browntowndev.enclave.core.data.local.ChatSummaryDao
import com.browntowndev.enclave.core.data.local.CleanupDao
import com.browntowndev.enclave.core.data.local.DefaultModelsDao
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.FirestoreSyncPlaceholderDao
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelsDao
import com.browntowndev.enclave.core.data.local.MediaProviderDao
import com.browntowndev.enclave.core.data.local.MemoriesDao
import com.browntowndev.enclave.core.data.local.MessageCommittedInferencePromptDao
import com.browntowndev.enclave.core.data.local.MessageFileDao
import com.browntowndev.enclave.core.data.local.MessageInferenceTelemetryDao
import com.browntowndev.enclave.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.enclave.core.data.local.ProfileAnalysisRunDao
import com.browntowndev.enclave.core.data.local.ProfileCorpusDao
import com.browntowndev.enclave.core.data.local.ProfileFindingsDao
import com.browntowndev.enclave.core.data.local.SQLiteVecInstaller
import com.browntowndev.enclave.core.data.local.StudioAlbumDao
import com.browntowndev.enclave.core.data.local.StudioMediaDao
import com.browntowndev.enclave.core.data.local.TelemetryEventDao
import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.local.UtilityModelsDao
import com.browntowndev.enclave.core.data.local.WebSearchSourceDao
import com.browntowndev.enclave.core.data.media.AndroidAudioCapture
import com.browntowndev.enclave.core.data.media.AndroidAudioPlayer
import com.browntowndev.enclave.core.data.media.AndroidAudioRecordFactory
import com.browntowndev.enclave.core.data.media.AndroidSaveBinaryToDownloadsAdapter
import com.browntowndev.enclave.core.data.media.AndroidShareMediaAdapter
import com.browntowndev.enclave.core.data.media.AndroidStreamingAudioPlayer
import com.browntowndev.enclave.core.data.media.AudioRecordFactory
import com.browntowndev.enclave.core.data.media.CachedImageAttachmentStorage
import com.browntowndev.enclave.core.data.remote.FirestoreDataSource
import com.browntowndev.enclave.core.data.remote.telemetry.OkHttpTelemetryOtlpTransport
import com.browntowndev.enclave.core.data.remote.telemetry.TelemetryOtlpClient
import com.browntowndev.enclave.core.data.remote.telemetry.TelemetryOtlpClientImpl
import com.browntowndev.enclave.core.data.remote.telemetry.TelemetryOtlpTransport
import com.browntowndev.enclave.core.data.repository.ActiveModelProviderImpl
import com.browntowndev.enclave.core.data.repository.ApiModelRepositoryImpl
import com.browntowndev.enclave.core.data.repository.ChatRepositoryImpl
import com.browntowndev.enclave.core.data.repository.CompositeToolExecutor
import com.browntowndev.enclave.core.data.repository.TrustedDomainRepositoryImpl
import com.browntowndev.enclave.core.data.repository.ModerationReportRepositoryImpl
import com.browntowndev.enclave.core.data.repository.DefaultModelRepositoryImpl
import com.browntowndev.enclave.core.data.repository.DeviceEnvironmentRepository
import com.browntowndev.enclave.core.data.repository.ExtractedUrlTracker
import com.browntowndev.enclave.core.data.repository.InferenceTelemetryRepositoryImpl
import com.browntowndev.enclave.core.data.repository.LocalModelRepositoryImpl
import com.browntowndev.enclave.core.data.repository.MediaProviderRepositoryImpl
import com.browntowndev.enclave.core.data.repository.MemoriesRepositoryImpl
import com.browntowndev.enclave.core.data.repository.MessageRepositoryImpl
import com.browntowndev.enclave.core.data.repository.ModelConfigProviderImpl
import com.browntowndev.enclave.core.data.repository.PipelineStateRepositoryImpl
import com.browntowndev.enclave.core.data.repository.RoomTransactionProvider
import com.browntowndev.enclave.core.data.repository.SettingsRepositoryImpl
import com.browntowndev.enclave.core.data.repository.StudioRepositoryImpl
import com.browntowndev.enclave.core.data.repository.TelemetryEventRepositoryImpl
import com.browntowndev.enclave.core.data.repository.ToolExecutionEventBus
import com.browntowndev.enclave.core.data.repository.TtsProviderRepositoryImpl
import com.browntowndev.enclave.core.data.repository.UtilityModelRepositoryImpl
import com.browntowndev.enclave.core.data.security.ApiKeyProviderImpl
import com.browntowndev.enclave.core.data.security.CryptoAtRestEncryptionService
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.core.data.security.LocalVaultMetadataStore
import com.browntowndev.enclave.core.data.security.SensitiveFieldCatalog
import com.browntowndev.enclave.core.data.security.StaticSensitiveFieldCatalog
import com.browntowndev.enclave.core.data.sync.TelemetryUploadConfig
import com.browntowndev.enclave.domain.port.auth.AuthManager
import com.browntowndev.enclave.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.enclave.domain.port.download.FileDownloaderPort
import com.browntowndev.enclave.domain.port.download.HashingPort
import com.browntowndev.enclave.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.enclave.domain.port.download.ModelFileScannerPort
import com.browntowndev.enclave.domain.port.download.ModelUrlProviderPort
import com.browntowndev.enclave.domain.port.inference.CommittedInferencePromptPort
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.media.AudioCapturePort
import com.browntowndev.enclave.domain.port.media.AudioPlayerPort
import com.browntowndev.enclave.domain.port.media.ImageAttachmentStoragePort
import com.browntowndev.enclave.domain.port.media.SaveBinaryToDownloadsPort
import com.browntowndev.enclave.domain.port.media.ShareMediaPort
import com.browntowndev.enclave.domain.port.media.StreamingAudioPlayerPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.DeviceEnvironmentRepositoryPort
import com.browntowndev.enclave.domain.port.repository.ExtractedUrlTrackerPort
import com.browntowndev.enclave.domain.port.repository.InferenceTelemetryRepositoryPort
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import com.browntowndev.enclave.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.enclave.domain.port.repository.ModelConfigProvider
import com.browntowndev.enclave.domain.port.repository.PipelineStateRepository
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import com.browntowndev.enclave.domain.port.repository.TrustedDomainRepositoryPort
import com.browntowndev.enclave.domain.port.repository.ModerationReportRepositoryPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import com.browntowndev.enclave.domain.port.repository.TtsProviderRepositoryPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.SyncOrchestrator
import com.browntowndev.enclave.core.data.sync.SyncOrchestratorImpl
import com.browntowndev.enclave.domain.port.security.ApiKeyProviderPort
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.domain.qualifier.ApplicationScope
import com.browntowndev.enclave.domain.qualifier.InternalTelemetryLogging
import com.browntowndev.enclave.domain.qualifier.PipelineDataStore
import com.browntowndev.enclave.domain.util.Clock
import com.browntowndev.enclave.domain.util.SystemClock
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val Context.pipelineDataStore: DataStore<Preferences> by preferencesDataStore(name = "pipeline_state")

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
object DataModule {
    @Provides
    @Singleton
    fun provideEnclaveDatabase(
        @ApplicationContext context: Context,
    ): EnclaveDatabase =
        Room
            .databaseBuilder(
                context,
                EnclaveDatabase::class.java,
                "enclave.db",
            ).fallbackToDestructiveMigration(dropAllTables = true)
            .openHelperFactory(SQLiteVecInstaller.createOpenHelperFactory(context))
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        SQLiteVecInstaller.createEmbeddingTable(db)
                        SQLiteVecInstaller.createMemoryEmbeddingTable(db)
                        SQLiteVecInstaller.createArtifactBlockEmbeddingTable(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        SQLiteVecInstaller.createEmbeddingTable(db)
                        SQLiteVecInstaller.createMemoryEmbeddingTable(db)
                        SQLiteVecInstaller.createArtifactBlockEmbeddingTable(db)
                    }
                },
            ).build()

    @Provides
    fun provideChatDao(database: EnclaveDatabase): ChatDao = database.chatDao()

    @Provides
    fun provideMessageQueryDao(database: EnclaveDatabase): com.browntowndev.enclave.core.data.local.dao.message.MessageQueryDao =
        database.messageQueryDao()

    @Provides
    fun provideMessageCommandDao(database: EnclaveDatabase): com.browntowndev.enclave.core.data.local.dao.message.MessageCommandDao =
        database.messageCommandDao()

    @Provides
    fun provideMessageSyncCommandDao(
        database: EnclaveDatabase,
    ): com.browntowndev.enclave.core.data.local.dao.message.MessageSyncCommandDao = database.messageSyncCommandDao()

    @Provides
    fun provideMessageTranscriptSegmentDao(database: EnclaveDatabase): MessageTranscriptSegmentDao = database.messageTranscriptSegmentDao()

    @Provides
    fun provideMessageFileDao(database: EnclaveDatabase): MessageFileDao = database.messageFileDao()

    @Provides
    fun provideChatSummaryDao(database: EnclaveDatabase): ChatSummaryDao = database.chatSummaryDao()

    @Provides
    fun provideWebSearchSourceDao(database: EnclaveDatabase): WebSearchSourceDao = database.webSearchSourceDao()

    @Provides
    fun provideFirestoreSyncPlaceholderDao(database: EnclaveDatabase): FirestoreSyncPlaceholderDao = database.firestoreSyncPlaceholderDao()

    @Provides
    fun provideMessageVisionAnalysisDao(database: EnclaveDatabase): MessageVisionAnalysisDao = database.messageVisionAnalysisDao()

    @Provides
    fun provideMessageInferenceTelemetryDao(database: EnclaveDatabase): MessageInferenceTelemetryDao =
        database.messageInferenceTelemetryDao()

    @Provides
    fun provideTelemetryEventDao(database: EnclaveDatabase): TelemetryEventDao = database.telemetryEventDao()

    @Provides
    @Singleton
    fun provideTelemetryOtlpTransport(
        okHttpClient: OkHttpClient,
        config: TelemetryUploadConfig,
        @InternalTelemetryLogging loggingPort: LoggingPort,
    ): TelemetryOtlpTransport = OkHttpTelemetryOtlpTransport(okHttpClient, config, loggingPort)

    @Provides
    @Singleton
    fun provideTelemetryOtlpClient(transport: TelemetryOtlpTransport): TelemetryOtlpClient = TelemetryOtlpClientImpl(transport)

    // Reflection-only helper for TEL-036; Hilt binding lives in AppBindingModule (TEL-055).
    fun provideTelemetryEventPort(impl: TelemetryEventRepositoryImpl): TelemetryEventPort = impl

    @Provides
    fun provideLocalModelsDao(database: EnclaveDatabase): LocalModelsDao = database.localModelsDao()

    @Provides
    fun provideUtilityModelsDao(database: EnclaveDatabase): UtilityModelsDao = database.utilityModelsDao()

    @Provides
    fun provideLocalModelConfigurationsDao(database: EnclaveDatabase): LocalModelConfigurationsDao = database.localModelConfigurationsDao()

    @Provides
    fun provideApiModelConfigurationsDao(database: EnclaveDatabase): ApiModelConfigurationsDao = database.apiModelConfigurationsDao()

    @Provides
    fun provideMemoriesDao(database: EnclaveDatabase): MemoriesDao = database.memoriesDao()

    @Provides
    fun provideApiCredentialsDao(database: EnclaveDatabase): ApiCredentialsDao = database.apiCredentialsDao()

    @Provides
    fun provideDefaultModelsDao(database: EnclaveDatabase): DefaultModelsDao = database.defaultModelsDao()

    @Provides
    fun provideEmbeddingDao(database: EnclaveDatabase): EmbeddingDao = database.embeddingDao()

    @Provides
    fun provideTtsProviderDao(database: EnclaveDatabase): TtsProviderDao = database.ttsProviderDao()

    @Provides
    fun provideMediaProviderDao(database: EnclaveDatabase): MediaProviderDao = database.mediaProviderDao()

    @Provides
    fun provideStudioMediaDao(database: EnclaveDatabase): StudioMediaDao = database.studioMediaDao()

    @Provides
    fun provideStudioAlbumDao(database: EnclaveDatabase): StudioAlbumDao = database.studioAlbumDao()

    @Provides
    fun provideDocumentDao(database: EnclaveDatabase): com.browntowndev.enclave.core.data.local.DocumentDao = database.documentDao()

    @Provides
    fun provideCleanupDao(database: EnclaveDatabase): CleanupDao = database.cleanupDao()

    @Provides
    fun provideMessageCommittedInferencePromptDao(database: EnclaveDatabase): MessageCommittedInferencePromptDao =
        database.messageCommittedInferencePromptDao()

    @Provides
    fun provideProfileCorpusDao(database: EnclaveDatabase): ProfileCorpusDao = database.profileCorpusDao()

    @Provides
    fun provideProfileFindingsDao(database: EnclaveDatabase): ProfileFindingsDao = database.profileFindingsDao()

    @Provides
    fun provideProfileAnalysisRunDao(database: EnclaveDatabase): ProfileAnalysisRunDao = database.profileAnalysisRunDao()

    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.dataStore

    @Provides
    @Singleton
    @PipelineDataStore
    fun providePipelineDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.pipelineDataStore

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): com.google.firebase.functions.FirebaseFunctions =
        com.google.firebase.functions.FirebaseFunctions
            .getInstance()

    @Provides
    @Singleton
    fun provideFirebaseStorage(): com.google.firebase.storage.FirebaseStorage =
        com.google.firebase.storage.FirebaseStorage
            .getInstance()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideUserIdProvider(firebaseAuth: FirebaseAuth): () -> String? =
        {
            firebaseAuth.currentUser?.uid
        }

    @Provides
    @Singleton
    fun provideCredentialManager(
        @ApplicationContext context: Context,
    ): CredentialManager = CredentialManager.create(context)

    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context,
    ): DownloadNotificationManager {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return DownloadNotificationManager(context, notificationManager)
    }

    @Provides
    @Singleton
    fun provideSystemClock(): Clock = SystemClock()

    @Provides
    @Singleton
    fun provideJavaTimeClock(): java.time.Clock = java.time.Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideZoneId(): java.time.ZoneId = java.time.ZoneId.systemDefault()

    @Provides
    @Singleton
    fun provideDownloadSpeedTracker(clock: Clock): DownloadSpeedTrackerPort = DownloadSpeedTracker(clock)

    @Provides
    @Singleton
    fun provideDownloadProgressTracker(speedTracker: DownloadSpeedTrackerPort): DownloadProgressTracker =
        DownloadProgressTracker(speedTracker)

    @Provides
    @Singleton
    fun provideHashingService(): HashingPort = HashingService()

    @Provides
    @Singleton
    fun provideHttpFileDownloader(
        okHttpClient: OkHttpClient,
        logger: LoggingPort,
    ): FileDownloaderPort = HttpFileDownloader(okHttpClient, logger)

    @Provides
    @Singleton
    fun provideModelUrlProvider(): ModelUrlProviderPort = DynamicModelUrlProvider()

    @Provides
    @Singleton
    fun provideEmbeddingEngine(engine: MiniLMEmbeddingEngine): EmbeddingEnginePort = engine

    @Provides
    @Singleton
    fun provideSensitiveFieldCatalog(): SensitiveFieldCatalog = StaticSensitiveFieldCatalog()

    @Provides
    @Singleton
    internal fun provideSearchToolSettingsSyncQueue(
        dataStore: DataStore<Preferences>,
        @ApplicationScope externalScope: CoroutineScope,
    ): com.browntowndev.enclave.core.data.sync.SearchToolSettingsSyncQueue =
        com.browntowndev.enclave.core.data.sync.SearchToolSettingsSyncQueue(
            dataStore = dataStore,
            syncScope = externalScope,
        )
}

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
abstract class DataRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindAuthManager(impl: FirebaseGoogleAuthManager): AuthManager

    @Binds
    @Singleton
    abstract fun bindCloudSyncPort(impl: FirestoreDataSource): CloudSyncPort

    @Binds
    @Singleton
    abstract fun bindBiometricPort(
        impl: com.browntowndev.enclave.core.data.security.AndroidBiometricManager,
    ): com.browntowndev.enclave.domain.port.security.BiometricPort

    @Binds
    @Singleton
    abstract fun bindCleanupRepository(
        impl: com.browntowndev.enclave.core.data.repository.CleanupRepositoryImpl,
    ): com.browntowndev.enclave.domain.port.repository.CleanupRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindInferenceTelemetryRepository(impl: InferenceTelemetryRepositoryImpl): InferenceTelemetryRepositoryPort

    @Binds
    @Singleton
    abstract fun bindMessageRepository(impl: MessageRepositoryImpl): MessageRepository

    @Binds
    @Singleton
    abstract fun bindMemoriesRepository(impl: MemoriesRepositoryImpl): MemoriesRepository

    @Binds
    @Singleton
    abstract fun bindExtractedUrlTracker(impl: ExtractedUrlTracker): ExtractedUrlTrackerPort

    @Binds
    @Singleton
    abstract fun bindDeviceEnvironmentRepository(impl: DeviceEnvironmentRepository): DeviceEnvironmentRepositoryPort

    @Binds
    @Singleton
    abstract fun bindImageAttachmentStorage(impl: CachedImageAttachmentStorage): ImageAttachmentStoragePort

    @Binds
    @Singleton
    abstract fun bindFileAttachmentPort(
        impl: com.browntowndev.enclave.core.data.media.AndroidFileAttachmentPort,
    ): com.browntowndev.enclave.domain.port.media.FileAttachmentPort

    @Binds
    @Singleton
    abstract fun bindAudioCapture(impl: AndroidAudioCapture): AudioCapturePort

    @Binds
    @Singleton
    abstract fun bindAudioPlayer(impl: AndroidAudioPlayer): AudioPlayerPort

    @Binds
    @Singleton
    abstract fun bindStreamingAudioPlayer(impl: AndroidStreamingAudioPlayer): StreamingAudioPlayerPort

    @Binds
    @Singleton
    abstract fun bindAudioRecordFactory(impl: AndroidAudioRecordFactory): AudioRecordFactory

    @Binds
    @Singleton
    abstract fun bindModelRegistry(impl: LocalModelRepositoryImpl): LocalModelRepositoryPort

    @Binds
    @Singleton
    abstract fun bindUtilityModelRepository(impl: UtilityModelRepositoryImpl): UtilityModelRepositoryPort

    @Binds
    @Singleton
    abstract fun bindModelConfigProvider(impl: ModelConfigProviderImpl): ModelConfigProvider

    @Binds
    @Singleton
    abstract fun bindActiveModelProvider(impl: ActiveModelProviderImpl): ActiveModelProviderPort

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindPipelineStateRepository(impl: PipelineStateRepositoryImpl): PipelineStateRepository

    @Binds
    @Singleton
    abstract fun bindTransactionProvider(impl: RoomTransactionProvider): TransactionProvider

    @Binds
    @Singleton
    abstract fun bindModelDownloadOrchestrator(impl: ModelDownloadOrchestratorImpl): ModelDownloadOrchestratorPort

    @Binds
    @Singleton
    abstract fun bindModelFileScanner(impl: ModelFileScanner): ModelFileScannerPort

    @Binds
    @Singleton
    abstract fun bindModelConfigFetcher(impl: ModelConfigFetcherImpl): ModelConfigFetcherPort

    @Binds
    @Singleton
    abstract fun bindTrustedDomainRepository(impl: TrustedDomainRepositoryImpl): TrustedDomainRepositoryPort

    @Binds
    @Singleton
    abstract fun bindModerationReportRepository(impl: ModerationReportRepositoryImpl): ModerationReportRepositoryPort

    @Binds
    @Singleton
    abstract fun bindUtilityModelFileResolver(impl: UtilityModelFileResolver): UtilityModelFilePort

    @Binds
    @Singleton
    abstract fun bindDefaultModelRepository(impl: DefaultModelRepositoryImpl): DefaultModelRepositoryPort

    @Binds
    @Singleton
    abstract fun bindApiModelRepository(impl: ApiModelRepositoryImpl): ApiModelRepositoryPort

    @Binds
    @Singleton
    abstract fun bindTtsProviderRepository(impl: TtsProviderRepositoryImpl): TtsProviderRepositoryPort

    @Binds
    @Singleton
    abstract fun bindMediaProviderRepository(impl: MediaProviderRepositoryImpl): MediaProviderRepositoryPort

    @Binds
    @Singleton
    abstract fun bindStudioRepository(impl: StudioRepositoryImpl): StudioRepositoryPort

    @Binds
    @Singleton
    abstract fun bindMediaSaver(
        impl: com.browntowndev.enclave.core.data.media.AndroidMediaSaver,
    ): com.browntowndev.enclave.domain.port.media.MediaSaverPort

    @Binds
    @Singleton
    abstract fun bindShareMediaPort(impl: AndroidShareMediaAdapter): ShareMediaPort

    @Binds
    @Singleton
    abstract fun bindSaveBinaryToDownloadsPort(impl: AndroidSaveBinaryToDownloadsAdapter): SaveBinaryToDownloadsPort

    @Binds
    @Singleton
    abstract fun bindShareRepository(
        impl: com.browntowndev.enclave.core.data.repository.FirestoreShareRepository,
    ): com.browntowndev.enclave.domain.port.repository.ShareRepositoryPort

    @Binds
    @Singleton
    abstract fun bindApiKeyProvider(impl: ApiKeyProviderImpl): ApiKeyProviderPort

    @Binds
    @Singleton
    abstract fun bindCryptoManager(
        impl: com.browntowndev.enclave.core.data.security.CryptoManagerImpl,
    ): com.browntowndev.enclave.domain.port.security.CryptoManagerPort

    @Binds
    @Singleton
    abstract fun bindLocalDataEncryptionService(impl: CryptoAtRestEncryptionService): LocalDataEncryptionService

    @Binds
    @Singleton
    abstract fun bindVaultMetadataPort(impl: LocalVaultMetadataStore): VaultMetadataPort

    @Binds
    @Singleton
    abstract fun bindToolExecutor(impl: CompositeToolExecutor): ToolExecutorPort

    @Binds
    @Singleton
    abstract fun bindToolExecutionEventPort(impl: ToolExecutionEventBus): ToolExecutionEventPort

    @Binds
    @Singleton
    abstract fun bindEmbeddingProgressPort(
        impl: com.browntowndev.enclave.core.data.sync.EmbeddingProgressManager,
    ): com.browntowndev.enclave.domain.port.repository.EmbeddingProgressPort

    @Binds
    @Singleton
    abstract fun bindCommittedInferencePromptPort(impl: RoomCommittedInferencePromptStore): CommittedInferencePromptPort

    @Binds
    @Singleton
    abstract fun bindProfileExportIngestionPort(
        impl: com.browntowndev.enclave.core.data.profile.ProfileExportIngestionService,
    ): com.browntowndev.enclave.domain.port.profile.ProfileExportIngestionPort

    @Binds
    @Singleton
    abstract fun bindConversationCorpusScorerPort(
        impl: com.browntowndev.enclave.core.data.profile.ProfileCorpusScorerImpl,
    ): com.browntowndev.enclave.domain.port.profile.ConversationCorpusScorerPort

    @Binds
    @Singleton
    abstract fun bindProfileAnalysisPort(
        impl: com.browntowndev.enclave.core.data.profile.analysis.ProfileAnalysisManager,
    ): com.browntowndev.enclave.domain.port.profile.ProfileAnalysisPort

    @Binds
    @Singleton
    abstract fun bindSyncOrchestrator(
        impl: SyncOrchestratorImpl,
    ): SyncOrchestrator
}
