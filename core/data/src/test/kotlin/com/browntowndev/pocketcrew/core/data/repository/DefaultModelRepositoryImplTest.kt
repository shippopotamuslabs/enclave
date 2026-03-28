package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiModelsDao
import com.browntowndev.pocketcrew.core.data.local.DefaultModelEntity
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.ModelsDao
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DefaultModelRepositoryImplTest {

    private lateinit var defaultModelsDao: DefaultModelsDao
    private lateinit var apiModelsDao: ApiModelsDao
    private lateinit var modelsDao: ModelsDao
    private lateinit var repository: DefaultModelRepositoryImpl

    @BeforeEach
    fun setUp() {
        defaultModelsDao = mockk(relaxed = true)
        apiModelsDao = mockk(relaxed = true)
        modelsDao = mockk(relaxed = true)
        repository = DefaultModelRepositoryImpl(defaultModelsDao, apiModelsDao, modelsDao)
    }

    // ========================================================================
    // Happy Path
    // ========================================================================

    @Test
    fun `getDefault returns ON_DEVICE assignment`() = runTest {
        coEvery { defaultModelsDao.getDefault(ModelType.FAST) } returns DefaultModelEntity(
            modelType = ModelType.FAST,
            source = ModelSource.ON_DEVICE,
            apiModelId = null,
        )

        val result = repository.getDefault(ModelType.FAST)
        assertEquals(ModelType.FAST, result?.modelType)
        assertEquals(ModelSource.ON_DEVICE, result?.source)
        assertNull(result?.apiModelConfig)
    }

    @Test
    fun `setDefault persists entity`() = runTest {
        repository.setDefault(ModelType.THINKING, ModelSource.API, 3L)

        coVerify {
            defaultModelsDao.upsert(
                match {
                    it.modelType == ModelType.THINKING &&
                        it.source == ModelSource.API &&
                        it.apiModelId == 3L
                },
            )
        }
    }

    @Test
    fun `setDefault to ON_DEVICE clears api model reference`() = runTest {
        // Set a default with an API model, then clear it to ON_DEVICE
        repository.setDefault(ModelType.FAST, ModelSource.API, 5L)
        repository.setDefault(ModelType.FAST, ModelSource.ON_DEVICE)

        coVerify {
            defaultModelsDao.upsert(
                match {
                    it.modelType == ModelType.FAST &&
                        it.source == ModelSource.ON_DEVICE &&
                        it.apiModelId == null
                },
            )
        }
    }

    @Test
    fun `clearDefault removes assignment`() = runTest {
        repository.clearDefault(ModelType.FAST)

        coVerify { defaultModelsDao.delete(ModelType.FAST) }
    }
}
