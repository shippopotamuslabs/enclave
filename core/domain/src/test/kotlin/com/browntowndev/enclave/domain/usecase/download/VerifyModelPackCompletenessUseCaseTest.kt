package com.browntowndev.enclave.domain.usecase.download

import com.browntowndev.enclave.core.testing.FakeModelConfigFetcher
import com.browntowndev.enclave.core.testing.FakeModelPackVerifierPort
import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackMemberStatus
import com.browntowndev.enclave.domain.model.download.ModelPackRole
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class VerifyModelPackCompletenessUseCaseTest {
    private lateinit var modelPackVerifier: FakeModelPackVerifierPort
    private lateinit var modelConfigFetcher: FakeModelConfigFetcher
    private lateinit var resolveModelCatalog: ResolveModelCatalogUseCase
    private lateinit var useCase: VerifyModelPackCompletenessUseCase

    @BeforeEach
    fun setup() {
        modelPackVerifier = FakeModelPackVerifierPort()
        modelConfigFetcher = FakeModelConfigFetcher()
        resolveModelCatalog = ResolveModelCatalogUseCase(modelConfigFetcher)
        useCase = VerifyModelPackCompletenessUseCase(modelPackVerifier, resolveModelCatalog)
    }

    @Test
    @DisplayName("MPK-008 Full pack completeness is 1/1")
    fun mpk008_fullPackCompleteness() =
        runTest {
            val completeness =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.PACK_ID,
                    installedCount = 1,
                    totalCount = 1,
                    isComplete = true,
                    members =
                        listOf(
                            memberStatus(ModelPackTestFixtures.DIFFUSION_FILE, true),
                        ),
                )
            modelConfigFetcher.persistedCatalogResult = Result.success(ModelPackTestFixtures.parsedCatalog())
            modelPackVerifier.completeness = completeness

            val result = useCase(ModelPackTestFixtures.PACK_ID).getOrThrow()

            assertTrue(result.isComplete)
            assertEquals(1, result.installedCount)
            assertEquals(1, result.totalCount)
        }

    @Test
    @DisplayName("MPK-017 Invalid on-disk hash counts as not installed")
    fun mpk017_invalidHashCountsAsNotInstalled() =
        runTest {
            val completeness =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.PACK_ID,
                    installedCount = 0,
                    totalCount = 1,
                    isComplete = false,
                    members =
                        listOf(
                            memberStatus(ModelPackTestFixtures.DIFFUSION_FILE, false),
                        ),
                )
            modelConfigFetcher.persistedCatalogResult = Result.success(ModelPackTestFixtures.parsedCatalog())
            modelPackVerifier.completeness = completeness

            val result = useCase(ModelPackTestFixtures.PACK_ID).getOrThrow()

            assertFalse(result.isComplete)
            assertFalse(result.members.first { it.fileName == ModelPackTestFixtures.DIFFUSION_FILE }.isVerifiedOnDisk)
        }

    private fun memberStatus(
        fileName: String,
        verified: Boolean,
    ) = ModelPackMemberStatus(ModelPackRole.DIFFUSION, fileName, verified)
}
