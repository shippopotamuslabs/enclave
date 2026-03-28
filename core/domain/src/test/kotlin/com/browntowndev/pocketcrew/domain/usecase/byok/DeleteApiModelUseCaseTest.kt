package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfig
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.FakeApiModelRepository
import com.browntowndev.pocketcrew.domain.usecase.FakeDefaultModelRepository
import com.browntowndev.pocketcrew.domain.usecase.FakeTransactionProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeleteApiModelUseCaseTest {

    private lateinit var apiRepo: FakeApiModelRepository
    private lateinit var defaultRepo: FakeDefaultModelRepository
    private lateinit var transactionProvider: FakeTransactionProvider
    private lateinit var useCase: DeleteApiModelUseCase

    @BeforeEach
    fun setUp() {
        apiRepo = FakeApiModelRepository()
        defaultRepo = FakeDefaultModelRepository()
        transactionProvider = FakeTransactionProvider()
        useCase = DeleteApiModelUseCase(apiRepo, defaultRepo, transactionProvider)
    }

    // ========================================================================
    // Happy Path
    // ========================================================================

    @Test
    fun `delete an existing API model`() = runTest {
        apiRepo.save(
            ApiModelConfig(displayName = "Test", provider = ApiProvider.ANTHROPIC, modelId = "m1"),
            "key",
        )
        assertEquals(1, apiRepo.getAll().size)

        useCase(1L)

        assertTrue(apiRepo.getAll().isEmpty())
    }

    @Test
    fun `delete a model that is a default assignment resets to ON_DEVICE`() = runTest {
        apiRepo.save(
            ApiModelConfig(displayName = "Test", provider = ApiProvider.ANTHROPIC, modelId = "m1"),
            "key",
        )
        defaultRepo.seed(
            listOf(
                DefaultModelAssignment(
                    ModelType.FAST,
                    ModelSource.API,
                    apiModelConfig = ApiModelConfig(id = 1, displayName = "Test", provider = ApiProvider.ANTHROPIC, modelId = "m1"),
                ),
            ),
        )

        useCase(1L)

        val fastDefault = defaultRepo.getDefault(ModelType.FAST)
        assertEquals(ModelSource.ON_DEVICE, fastDefault?.source)
        assertNull(fastDefault?.apiModelConfig)
    }

    @Test
    fun `delete a model assigned to multiple slots resets all of them`() = runTest {
        apiRepo.save(
            ApiModelConfig(displayName = "Multi", provider = ApiProvider.OPENAI, modelId = "m1"),
            "key",
        )
        val config = ApiModelConfig(id = 1, displayName = "Multi", provider = ApiProvider.OPENAI, modelId = "m1")
        defaultRepo.seed(
            listOf(
                DefaultModelAssignment(ModelType.FAST, ModelSource.API, apiModelConfig = config),
                DefaultModelAssignment(ModelType.THINKING, ModelSource.API, apiModelConfig = config),
                DefaultModelAssignment(ModelType.MAIN, ModelSource.ON_DEVICE, onDeviceDisplayName = "Gemma 3n"),
            ),
        )

        useCase(1L)

        assertEquals(ModelSource.ON_DEVICE, defaultRepo.getDefault(ModelType.FAST)?.source)
        assertEquals(ModelSource.ON_DEVICE, defaultRepo.getDefault(ModelType.THINKING)?.source)
        assertEquals(ModelSource.ON_DEVICE, defaultRepo.getDefault(ModelType.MAIN)?.source)
    }

    // ========================================================================
    // Edge Cases
    // ========================================================================

    @Test
    fun `delete non-existent model is a no-op`() = runTest {
        useCase(999L)
        assertTrue(apiRepo.getAll().isEmpty())
    }

    @Test
    fun `delete model with no default assignments does not affect defaults`() = runTest {
        apiRepo.save(
            ApiModelConfig(displayName = "Test", provider = ApiProvider.ANTHROPIC, modelId = "m1"),
            "key",
        )
        defaultRepo.seed(
            listOf(DefaultModelAssignment(ModelType.FAST, ModelSource.ON_DEVICE, onDeviceDisplayName = "Gemma")),
        )

        useCase(1L)

        val fastDefault = defaultRepo.getDefault(ModelType.FAST)
        assertEquals(ModelSource.ON_DEVICE, fastDefault?.source)
        assertEquals("Gemma", fastDefault?.onDeviceDisplayName)
    }

    // ========================================================================
    // Mutation Defense
    // ========================================================================

    @Test
    fun `delete cascades to default assignments`() = runTest {
        apiRepo.save(
            ApiModelConfig(displayName = "Test", provider = ApiProvider.ANTHROPIC, modelId = "m1"),
            "key",
        )
        val config = ApiModelConfig(id = 1, displayName = "Test", provider = ApiProvider.ANTHROPIC, modelId = "m1")
        defaultRepo.seed(
            listOf(
                DefaultModelAssignment(ModelType.FAST, ModelSource.API, apiModelConfig = config),
                DefaultModelAssignment(ModelType.THINKING, ModelSource.API, apiModelConfig = config),
                DefaultModelAssignment(ModelType.MAIN, ModelSource.ON_DEVICE, onDeviceDisplayName = "Gemma"),
            ),
        )

        useCase(1L)

        assertEquals(ModelSource.ON_DEVICE, defaultRepo.getDefault(ModelType.FAST)?.source)
        assertEquals(ModelSource.ON_DEVICE, defaultRepo.getDefault(ModelType.THINKING)?.source)
        // MAIN was already ON_DEVICE — should be unchanged
        assertEquals(ModelSource.ON_DEVICE, defaultRepo.getDefault(ModelType.MAIN)?.source)
        assertEquals("Gemma", defaultRepo.getDefault(ModelType.MAIN)?.onDeviceDisplayName)
    }
}
