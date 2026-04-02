package com.browntowndev.pocketcrew.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelRegistryImplTest {
    private lateinit var database: PocketCrewDatabase
    private lateinit var repository: ModelRegistryImpl
    private val transactionProvider = mockk<TransactionProvider>()
    private val logger = mockk<LoggingPort>(relaxed = true)

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PocketCrewDatabase::class.java
        ).allowMainThreadQueries().build()

        coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
            (args[0] as suspend () -> Any).invoke()
        }
        every { logger.debug(any(), any()) } returns Unit

        repository = ModelRegistryImpl(
            modelsDao = database.localModelsDao(),
            configsDao = database.localModelConfigurationsDao(),
            defaultModelsDao = database.defaultModelsDao(),
            transactionProvider = transactionProvider,
            logger = logger
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `setRegisteredModel preserves visionCapable metadata through persistence`() = runTest {
        val asset = LocalModelAsset(
            metadata = LocalModelMetadata(
                huggingFaceModelName = "model/vision",
                remoteFileName = "vision.task",
                localFileName = "vision.task",
                sha256 = "vision-sha",
                sizeInBytes = 2048L,
                modelFileFormat = ModelFileFormat.TASK,
                visionCapable = true
            ),
            configurations = listOf(
                LocalModelConfiguration(
                    localModelId = 0,
                    displayName = "Vision",
                    maxTokens = 4096,
                    contextWindow = 4096,
                    temperature = 0.7,
                    topP = 0.95,
                    topK = 40,
                    repetitionPenalty = 1.1,
                    systemPrompt = ""
                )
            )
        )

        repository.setRegisteredModel(
            modelType = ModelType.VISION,
            asset = asset,
            status = ModelStatus.CURRENT
        )

        val persisted = repository.getRegisteredAsset(ModelType.VISION)

        assertNotNull(persisted)
        assertTrue(persisted!!.metadata.visionCapable)
    }
}
