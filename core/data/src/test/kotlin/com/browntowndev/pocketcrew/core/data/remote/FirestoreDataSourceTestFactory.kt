package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.ChatDao
import com.browntowndev.pocketcrew.core.data.local.ChatSummaryDao
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.DocumentDao
import com.browntowndev.pocketcrew.core.data.local.EmbeddingDao
import com.browntowndev.pocketcrew.core.data.local.FirestoreSyncPlaceholderDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.core.data.local.MediaProviderDao
import com.browntowndev.pocketcrew.core.data.local.MemoriesDao
import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.local.MessageFileDao
import com.browntowndev.pocketcrew.core.data.local.MessageTranscriptSegmentDao
import com.browntowndev.pocketcrew.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.pocketcrew.core.data.local.StudioAlbumDao
import com.browntowndev.pocketcrew.core.data.local.StudioMediaDao
import com.browntowndev.pocketcrew.core.data.local.TtsProviderDao
import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceDao
import com.browntowndev.pocketcrew.core.data.remote.firestore.ChatSyncDaos
import com.browntowndev.pocketcrew.core.data.remote.firestore.ConfigSyncDaos
import com.browntowndev.pocketcrew.core.data.remote.firestore.FirestoreSyncDaos
import com.browntowndev.pocketcrew.core.data.remote.firestore.StudioSyncDaos
import com.browntowndev.pocketcrew.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.core.data.sync.EmbeddingRegenerationCoordinator
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("LongParameterList")
internal fun createTestFirestoreDataSource(
    firestore: FirebaseFirestore,
    chatDao: ChatDao = mockk(relaxed = true),
    messageDao: MessageDao = mockk(relaxed = true),
    messageTranscriptSegmentDao: MessageTranscriptSegmentDao = mockk(relaxed = true),
    memoriesDao: MemoriesDao = mockk(relaxed = true),
    webSearchSourceDao: WebSearchSourceDao = mockk(relaxed = true),
    ttsProviderDao: TtsProviderDao = mockk(relaxed = true),
    mediaProviderDao: MediaProviderDao = mockk(relaxed = true),
    apiModelConfigurationsDao: ApiModelConfigurationsDao = mockk(relaxed = true),
    localModelsDao: LocalModelsDao = mockk(relaxed = true),
    apiCredentialsDao: ApiCredentialsDao = mockk(relaxed = true),
    localModelConfigurationsDao: LocalModelConfigurationsDao = mockk(relaxed = true),
    defaultModelsDao: DefaultModelsDao = mockk(relaxed = true),
    studioMediaDao: StudioMediaDao = mockk(relaxed = true),
    studioAlbumDao: StudioAlbumDao = mockk(relaxed = true),
    messageFileDao: MessageFileDao = mockk(relaxed = true),
    messageVisionAnalysisDao: MessageVisionAnalysisDao = mockk(relaxed = true),
    chatSummaryDao: ChatSummaryDao = mockk(relaxed = true),
    documentDao: DocumentDao = mockk(relaxed = true),
    embeddingDao: EmbeddingDao = mockk(relaxed = true),
    firestoreSyncPlaceholderDao: FirestoreSyncPlaceholderDao = mockk(relaxed = true),
    apiKeyManager: ApiKeyManager = mockk(relaxed = true),
    cryptoManager: CryptoManagerPort = mockk(relaxed = true),
    loggingPort: LoggingPort = mockk(relaxed = true),
    externalScope: CoroutineScope,
    userIdProvider: () -> String? = { "test-user-id" },
    embeddingCoordinator: EmbeddingRegenerationCoordinator = mockk(relaxed = true),
    localEncryption: LocalDataEncryptionService = FakeLocalDataEncryptionService(),
    sensitiveSelfHealer: SensitivePersistenceSelfHealer? = null,
    purgeInProgress: AtomicBoolean = AtomicBoolean(false),
): FirestoreDataSource {
    val healer = sensitiveSelfHealer ?: SensitivePersistenceSelfHealer(localEncryption, loggingPort)
    val facade =
        FirestoreSyncDependenciesFacade.forTests(
            firestore = firestore,
            chatSyncDaos =
                ChatSyncDaos(
                    chatDao = chatDao,
                    messageDao = messageDao,
                    chatSummaryDao = chatSummaryDao,
                    messageFileDao = messageFileDao,
                    messageVisionAnalysisDao = messageVisionAnalysisDao,
                    messageTranscriptSegmentDao = messageTranscriptSegmentDao,
                    webSearchSourceDao = webSearchSourceDao,
                    documentDao = documentDao,
                    embeddingDao = embeddingDao,
                    firestoreSyncPlaceholderDao = firestoreSyncPlaceholderDao,
                ),
            configSyncDaos =
                ConfigSyncDaos(
                    memoriesDao = memoriesDao,
                    ttsProviderDao = ttsProviderDao,
                    mediaProviderDao = mediaProviderDao,
                    apiModelConfigurationsDao = apiModelConfigurationsDao,
                    localModelsDao = localModelsDao,
                    apiCredentialsDao = apiCredentialsDao,
                    localModelConfigurationsDao = localModelConfigurationsDao,
                    defaultModelsDao = defaultModelsDao,
                ),
            studioSyncDaos =
                StudioSyncDaos(
                    studioMediaDao = studioMediaDao,
                    studioAlbumDao = studioAlbumDao,
                ),
            apiKeyManager = apiKeyManager,
            cryptoManager = cryptoManager,
            loggingPort = loggingPort,
            userIdProvider = userIdProvider,
            embeddingCoordinator = embeddingCoordinator,
            localEncryption = localEncryption,
            sensitiveSelfHealer = healer,
            purgeInProgress = purgeInProgress,
        )
    return FirestoreDataSource(facade, externalScope)
}

internal fun testFirestoreSyncDaos(
    chatDao: ChatDao = mockk(relaxed = true),
    messageDao: MessageDao = mockk(relaxed = true),
    messageTranscriptSegmentDao: MessageTranscriptSegmentDao = mockk(relaxed = true),
    memoriesDao: MemoriesDao = mockk(relaxed = true),
    webSearchSourceDao: WebSearchSourceDao = mockk(relaxed = true),
    ttsProviderDao: TtsProviderDao = mockk(relaxed = true),
    mediaProviderDao: MediaProviderDao = mockk(relaxed = true),
    apiModelConfigurationsDao: ApiModelConfigurationsDao = mockk(relaxed = true),
    localModelsDao: LocalModelsDao = mockk(relaxed = true),
    apiCredentialsDao: ApiCredentialsDao = mockk(relaxed = true),
    localModelConfigurationsDao: LocalModelConfigurationsDao = mockk(relaxed = true),
    defaultModelsDao: DefaultModelsDao = mockk(relaxed = true),
    studioMediaDao: StudioMediaDao = mockk(relaxed = true),
    studioAlbumDao: StudioAlbumDao = mockk(relaxed = true),
    messageFileDao: MessageFileDao = mockk(relaxed = true),
    messageVisionAnalysisDao: MessageVisionAnalysisDao = mockk(relaxed = true),
    chatSummaryDao: ChatSummaryDao = mockk(relaxed = true),
    documentDao: DocumentDao = mockk(relaxed = true),
    embeddingDao: EmbeddingDao = mockk(relaxed = true),
    firestoreSyncPlaceholderDao: FirestoreSyncPlaceholderDao = mockk(relaxed = true),
): FirestoreSyncDaos =
    FirestoreSyncDaos.from(
        chatSyncDaos =
            ChatSyncDaos(
                chatDao = chatDao,
                messageDao = messageDao,
                chatSummaryDao = chatSummaryDao,
                messageFileDao = messageFileDao,
                messageVisionAnalysisDao = messageVisionAnalysisDao,
                messageTranscriptSegmentDao = messageTranscriptSegmentDao,
                webSearchSourceDao = webSearchSourceDao,
                documentDao = documentDao,
                embeddingDao = embeddingDao,
                firestoreSyncPlaceholderDao = firestoreSyncPlaceholderDao,
            ),
        configSyncDaos =
            ConfigSyncDaos(
                memoriesDao = memoriesDao,
                ttsProviderDao = ttsProviderDao,
                mediaProviderDao = mediaProviderDao,
                apiModelConfigurationsDao = apiModelConfigurationsDao,
                localModelsDao = localModelsDao,
                apiCredentialsDao = apiCredentialsDao,
                localModelConfigurationsDao = localModelConfigurationsDao,
                defaultModelsDao = defaultModelsDao,
            ),
        studioSyncDaos = StudioSyncDaos(studioMediaDao = studioMediaDao, studioAlbumDao = studioAlbumDao),
    )
