@file:Suppress("ForbiddenMockKInTests")
// MockK is used only for Firebase SDK classes (FirebaseFirestore, DocumentSnapshot/Reference, CollectionReference), third-party SDK types with no hand-writable fakes (ENC-28).

package com.browntowndev.enclave.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.local.EnclaveDatabase
import com.browntowndev.enclave.core.data.local.MediaProviderEntity
import com.browntowndev.enclave.core.data.remote.FirestoreSensitiveFieldRepairer
import com.browntowndev.enclave.core.data.remote.firestore.FirestoreSyncContext
import com.browntowndev.enclave.core.data.remote.firestore.handlers.MediaProviderSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.testEmbeddingRegenerationCoordinator
import com.browntowndev.enclave.core.data.remote.testFirestoreSyncDaos
import com.browntowndev.enclave.core.data.repository.FakeLocalDataEncryptionService
import com.browntowndev.enclave.core.data.repository.MediaProviderRepositoryImpl
import com.browntowndev.enclave.core.data.repository.realReadRepairApplier
import com.browntowndev.enclave.core.data.repository.testSensitiveSelfHealer
import com.browntowndev.enclave.core.data.security.StaticSensitiveFieldCatalog
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.MediaTask
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.concurrent.atomic.AtomicBoolean

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class MediaTaskPersistenceRedTest {
    private val mediaProviderDao = FakeMediaProviderDao()

    // Lazy so DB-01 (annotation read) and SYNC-01/02/03 (handler + fakes) never
    // open a real database or WorkManager (ENC-253). Only ENTITY tests build these.
    private val databaseLazy =
        lazy {
            val context = ApplicationProvider.getApplicationContext<Context>()
            Room
                .inMemoryDatabaseBuilder(context, EnclaveDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        }
    private val database: EnclaveDatabase by databaseLazy

    private val repositoryLazy =
        lazy {
            MediaProviderRepositoryImpl(
                syncTrigger = FakeCloudSyncTrigger(),
                mediaProviderDao = mediaProviderDao,
                apiKeyManager = FakeApiKeyManager(),
                healer = testSensitiveSelfHealer(FakeLocalDataEncryptionService()),
                readRepairApplier = database.realReadRepairApplier(),
            )
        }
    private val repository: MediaProviderRepositoryImpl by repositoryLazy

    @AfterEach
    fun teardown() {
        if (databaseLazy.isInitialized()) {
            databaseLazy.value.close()
        }
    }

    @Test
    fun `ENTITY-01 entity to domain mapper reads tasks_mask`() =
        runTest {
            mediaProviderDao.observeAllResult =
                flowOf(
                    listOf(
                        MediaProviderEntity(
                            id = "provider",
                            displayName = "Fal",
                            provider = ApiProvider.FAL,
                            capability = MediaCapability.IMAGE,
                            modelName = "fal-ai/flux-pro/kontext",
                            baseUrl = null,
                            credentialAlias = "alias",
                            tasksMask = 3,
                        ),
                    ),
                )

            val result = repository.getMediaProviders().first().single()

            assertEquals(setOf(MediaTask.TEXT_TO_IMAGE, MediaTask.EDIT_IMAGE), result.tasks)
            assertEquals(MediaCapability.IMAGE, result.capability)
        }

    @Test
    fun `ENTITY-03 unknown tasks_mask seeds legacy tasks for capability`() =
        runTest {
            mediaProviderDao.observeAllResult =
                flowOf(
                    listOf(
                        MediaProviderEntity(
                            id = "legacy",
                            displayName = "Legacy",
                            provider = ApiProvider.OPENAI,
                            capability = MediaCapability.IMAGE,
                            modelName = "dall-e-3",
                            baseUrl = null,
                            credentialAlias = "alias",
                            tasksMask = 0,
                        ),
                    ),
                )

            val result = repository.getMediaProviders().first().single()

            assertEquals(setOf(MediaTask.TEXT_TO_IMAGE, MediaTask.EDIT_IMAGE), result.tasks)
        }

    @Test
    fun `ENTITY-02 domain to entity save writes tasks_mask`() =
        runTest {
            repository.saveMediaProvider(
                MediaProviderAsset(
                    id = MediaProviderId("provider"),
                    displayName = "Fal",
                    provider = ApiProvider.FAL,
                    capability = MediaCapability.VIDEO,
                    modelName = "fal-ai/kling-video/v2.1/master/image-to-video",
                    baseUrl = null,
                    credentialAlias = "alias",
                    tasks = setOf(MediaTask.IMAGE_TO_VIDEO),
                ),
                apiKey = "key",
            )

            assertTrue(mediaProviderDao.upsertCalls.any { it.tasksMask == 8 })
        }

    @Test
    fun `DB-01 EnclaveDatabase version matches current schema`() {
        assertEquals(5, databaseAnnotationVersion())
    }

    @Test
    fun `SYNC-01 toFirestore includes tasks_mask`() =
        runTest {
            val handler = MediaProviderSyncHandler(createContext())

            val payload =
                handler.toFirestore(
                    mediaProviderEntity(tasksMask = 12),
                )

            assertEquals(12, payload["tasks_mask"])
        }

    @Test
    fun `SYNC-02 fromFirestore reads tasks_mask`() {
        val handler = MediaProviderSyncHandler(createContext())
        val entity = handler.fromFirestore(mediaProviderDocument(tasksMask = 12))

        assertEquals(12, entity.tasksMask)
    }

    @Test
    fun `SYNC-03 fromFirestore defaults missing tasks_mask to zero`() {
        val handler = MediaProviderSyncHandler(createContext())
        val entity = handler.fromFirestore(mediaProviderDocument(tasksMask = null))

        assertEquals(0, entity.tasksMask)
    }

    private fun createContext(): FirestoreSyncContext {
        val localEncryption = FakeLocalDataEncryptionService()
        val loggingPort = FakeLoggingPort()
        val healer = testSensitiveSelfHealer(localEncryption)
        return FirestoreSyncContext(
            firestore = mockk<FirebaseFirestore>(relaxed = true),
            daos = testFirestoreSyncDaos(mediaProviderDao = mediaProviderDao),
            localEncryption = localEncryption,
            sensitiveSelfHealer = healer,
            firestoreSensitiveFieldRepairer =
                FirestoreSensitiveFieldRepairer(
                    catalog = StaticSensitiveFieldCatalog(),
                    healer = healer,
                ),
            cryptoManager = FakeCryptoManagerPort(),
            loggingPort = loggingPort,
            apiKeyManager = FakeApiKeyManager(),
            embeddingCoordinator = testEmbeddingRegenerationCoordinator(),
            userIdProvider = { "user-1" },
            purgeInProgress = AtomicBoolean(false),
        )
    }

    private fun mediaProviderEntity(tasksMask: Int): MediaProviderEntity =
        MediaProviderEntity(
            id = "provider",
            displayName = "Fal",
            provider = ApiProvider.FAL,
            capability = MediaCapability.VIDEO,
            modelName = "fal-ai/minimax/video-01",
            baseUrl = null,
            credentialAlias = "alias",
            tasksMask = tasksMask,
        )

    private fun mediaProviderDocument(tasksMask: Long?): DocumentSnapshot {
        val snapshot = mockk<DocumentSnapshot>(relaxed = true)
        val reference = mockk<DocumentReference>(relaxed = true)
        val collection = mockk<CollectionReference>(relaxed = true)
        every { snapshot.id } returns "provider"
        every { snapshot.reference } returns reference
        every { reference.parent } returns collection
        every { collection.id } returns "media_providers"
        every { snapshot.getString("display_name") } returns "Fal"
        every { snapshot.getString("provider") } returns ApiProvider.FAL.name
        every { snapshot.getString("capability") } returns MediaCapability.VIDEO.name
        every { snapshot.getString("model_name") } returns "fal-ai/minimax/video-01"
        every { snapshot.getString("base_url") } returns null
        every { snapshot.getString("credential_alias") } returns "alias"
        every { snapshot.getLong("updated_at") } returns 1L
        every { snapshot.getBoolean("deleted") } returns false
        every { snapshot.getLong("tasks_mask") } returns tasksMask
        return snapshot
    }

    private fun databaseAnnotationVersion(): Int {
        var version = -1
        val classBytes =
            requireNotNull(EnclaveDatabase::class.java.getResourceAsStream("EnclaveDatabase.class")) {
                "EnclaveDatabase class bytes must be available for @Database annotation inspection."
            }.readBytes()
        ClassReader(classBytes).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitAnnotation(
                    descriptor: String,
                    visible: Boolean,
                ): AnnotationVisitor? {
                    if (descriptor != "Landroidx/room/Database;") return null
                    return object : AnnotationVisitor(Opcodes.ASM9) {
                        override fun visit(
                            name: String,
                            value: Any,
                        ) {
                            if (name == "version") {
                                version = value as Int
                            }
                        }
                    }
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )
        return version
    }
}
