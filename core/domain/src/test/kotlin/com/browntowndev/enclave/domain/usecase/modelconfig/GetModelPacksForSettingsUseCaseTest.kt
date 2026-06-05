package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import com.browntowndev.enclave.domain.port.download.ModelPackVerifierPort
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.usecase.download.VerifyModelPackCompletenessUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GetModelPacksForSettingsUseCaseTest {
    private lateinit var resolveModelCatalog: ResolveModelCatalogUseCase
    private lateinit var verifyModelPackCompleteness: VerifyModelPackCompletenessUseCase
    private lateinit var localModelRepository: LocalModelRepositoryPort
    private lateinit var utilityModelRepository: UtilityModelRepositoryPort
    private lateinit var modelPackVerifier: ModelPackVerifierPort
    private lateinit var useCase: GetModelPacksForSettingsUseCase

    @BeforeEach
    fun setup() {
        resolveModelCatalog = mockk()
        verifyModelPackCompleteness = mockk()
        modelPackVerifier = mockk()
        localModelRepository = mockk(relaxed = true)
        utilityModelRepository = mockk(relaxed = true)
        coEvery { resolveModelCatalog() } returns Result.success(ModelPackTestFixtures.parsedCatalog())
        useCase =
            GetModelPacksForSettingsUseCase(
                resolveModelCatalog,
                verifyModelPackCompleteness,
                modelPackVerifier,
                localModelRepository,
                utilityModelRepository,
            )
    }

    @Test
    fun returnsPackRowsFromCatalogWhenOnDeviceImageGenerationEnabled() =
        runTest {
            coEvery { localModelRepository.getSoftDeletedModels() } returns emptyList()
            coEvery { utilityModelRepository.getByUtilityType(any()) } returns null
            coEvery { verifyModelPackCompleteness(ModelPackTestFixtures.PACK_ID) } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = ModelPackTestFixtures.PACK_ID,
                        installedCount = 1,
                        totalCount = 1,
                        isComplete = true,
                        members = emptyList(),
                    ),
                )

            val rows = useCase()

            assertEquals(ModelPackTestFixtures.PACK_ID, rows.single().pack.packId)
        }

    @Test
    fun returnsEmptyWhenCatalogHasNoModelPacks() =
        runTest {
            coEvery { resolveModelCatalog() } returns
                Result.success(ModelPackTestFixtures.parsedCatalog(includePack = false))

            val rows = useCase()

            assertTrue(rows.isEmpty())
        }
}
