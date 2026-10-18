package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.core.testing.FakeDownloadWorkSchedulerPort
import com.browntowndev.enclave.core.testing.FakeModelConfigFetcher
import com.browntowndev.enclave.core.testing.FakeModelPackVerifierPort
import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackMemberStatus
import com.browntowndev.enclave.domain.model.download.ModelPackRole
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.usecase.download.VerifyModelPackCompletenessUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ReDownloadModelPackUseCaseTest {
    private lateinit var downloadWorkScheduler: FakeDownloadWorkSchedulerPort
    private lateinit var modelPackVerifier: FakeModelPackVerifierPort
    private lateinit var useCase: ReDownloadModelPackUseCase

    @BeforeEach
    fun setup() {
        downloadWorkScheduler = FakeDownloadWorkSchedulerPort()
        modelPackVerifier = FakeModelPackVerifierPort()
        val modelConfigFetcher =
            FakeModelConfigFetcher().apply {
                persistedCatalogResult = Result.success(ModelPackTestFixtures.parsedCatalog())
            }
        val resolveModelCatalog = ResolveModelCatalogUseCase(modelConfigFetcher)
        useCase =
            ReDownloadModelPackUseCase(
                downloadWorkScheduler,
                VerifyModelPackCompletenessUseCase(modelPackVerifier, resolveModelCatalog),
                resolveModelCatalog,
            )
    }

    @Test
    @DisplayName("MPK-009 Re-download enqueues only missing pack member")
    fun mpk009_reDownloadEnqueuesOnlyMissingMember() =
        runTest {
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.PACK_ID,
                    installedCount = 0,
                    totalCount = 1,
                    isComplete = false,
                    members =
                        listOf(
                            member(ModelPackTestFixtures.DIFFUSION_FILE, false),
                        ),
                )

            val scheduled = useCase(ModelPackTestFixtures.PACK_ID).getOrThrow()

            assertEquals(1, scheduled.size)
            assertEquals(1, downloadWorkScheduler.enqueued.size)
            assertEquals(
                ModelPackTestFixtures.DIFFUSION_FILE,
                downloadWorkScheduler.enqueued.single().files.single().remoteFileName,
            )
        }

    @Test
    @DisplayName("MPK-021 Re-download must not enqueue verified members")
    fun mpk021_reDownloadDoesNotEnqueueWhenComplete() =
        runTest {
            modelPackVerifier.completenessByPackId[ModelPackTestFixtures.PACK_ID] =
                ModelPackCompleteness(
                    packId = ModelPackTestFixtures.PACK_ID,
                    installedCount = 1,
                    totalCount = 1,
                    isComplete = true,
                    members = emptyList(),
                )

            val scheduled = useCase(ModelPackTestFixtures.PACK_ID).getOrThrow()

            assertTrue(scheduled.isEmpty())
        }

    private fun member(
        fileName: String,
        verified: Boolean,
    ) = ModelPackMemberStatus(ModelPackRole.DIFFUSION, fileName, verified)
}
