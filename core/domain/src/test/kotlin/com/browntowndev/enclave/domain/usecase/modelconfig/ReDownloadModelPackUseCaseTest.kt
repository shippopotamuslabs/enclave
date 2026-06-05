package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackMemberStatus
import com.browntowndev.enclave.domain.model.download.ModelPackRole
import com.browntowndev.enclave.domain.model.download.ModelPackTestFixtures
import com.browntowndev.enclave.domain.port.download.DownloadWorkSchedulerPort
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.usecase.download.VerifyModelPackCompletenessUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ReDownloadModelPackUseCaseTest {
    private lateinit var downloadWorkScheduler: DownloadWorkSchedulerPort
    private lateinit var verifyModelPackCompleteness: VerifyModelPackCompletenessUseCase
    private lateinit var resolveModelCatalog: ResolveModelCatalogUseCase
    private lateinit var useCase: ReDownloadModelPackUseCase

    @BeforeEach
    fun setup() {
        downloadWorkScheduler = mockk(relaxed = true)
        verifyModelPackCompleteness = mockk()
        resolveModelCatalog = mockk()
        coEvery { resolveModelCatalog() } returns Result.success(ModelPackTestFixtures.parsedCatalog())
        useCase =
            ReDownloadModelPackUseCase(
                downloadWorkScheduler,
                verifyModelPackCompleteness,
                resolveModelCatalog,
            )
    }

    @Test
    @DisplayName("MPK-009 Re-download enqueues only missing pack member")
    fun mpk009_reDownloadEnqueuesOnlyMissingMember() =
        runTest {
            coEvery { verifyModelPackCompleteness(ModelPackTestFixtures.PACK_ID) } returns
                Result.success(
                    ModelPackCompleteness(
                        packId = ModelPackTestFixtures.PACK_ID,
                        installedCount = 0,
                        totalCount = 1,
                        isComplete = false,
                        members =
                            listOf(
                                member(ModelPackTestFixtures.DIFFUSION_FILE, false),
                            ),
                    ),
                )

            val scheduled = useCase(ModelPackTestFixtures.PACK_ID).getOrThrow()

            assertEquals(1, scheduled.size)
            coVerify(exactly = 1) {
                downloadWorkScheduler.enqueue(
                    match { request ->
                        request.files.single().remoteFileName == ModelPackTestFixtures.DIFFUSION_FILE
                    },
                )
            }
        }

    @Test
    @DisplayName("MPK-021 Re-download must not enqueue verified members")
    fun mpk021_reDownloadDoesNotEnqueueWhenComplete() =
        runTest {
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

            val scheduled = useCase(ModelPackTestFixtures.PACK_ID).getOrThrow()

            assertTrue(scheduled.isEmpty())
        }

    private fun member(
        fileName: String,
        verified: Boolean,
    ) = ModelPackMemberStatus(ModelPackRole.DIFFUSION, fileName, verified)
}