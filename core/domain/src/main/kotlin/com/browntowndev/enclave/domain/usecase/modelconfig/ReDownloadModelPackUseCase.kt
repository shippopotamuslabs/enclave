package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.download.DownloadFileSpec
import com.browntowndev.enclave.domain.model.download.DownloadRequestKind
import com.browntowndev.enclave.domain.model.download.DownloadWorkRequest
import com.browntowndev.enclave.domain.model.download.ScheduledDownload
import com.browntowndev.enclave.domain.port.download.DownloadWorkSchedulerPort
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.usecase.download.VerifyModelPackCompletenessUseCase
import java.util.UUID
import javax.inject.Inject

class ReDownloadModelPackUseCase
    @Inject
    constructor(
        private val downloadWorkScheduler: DownloadWorkSchedulerPort,
        private val verifyModelPackCompleteness: VerifyModelPackCompletenessUseCase,
        private val resolveModelCatalog: ResolveModelCatalogUseCase,
    ) {
        suspend operator fun invoke(packId: String): Result<List<ScheduledDownload>> =
            runCatching {
                val completeness = verifyModelPackCompleteness(packId).getOrThrow()
                if (completeness.isComplete) {
                    return@runCatching emptyList()
                }

                val catalog = resolveModelCatalog().getOrThrow()
                val pack =
                    catalog.packById(packId)
                        ?: throw IllegalArgumentException("Unknown model pack: $packId")

                val missingFileNames =
                    completeness.members
                        .filterNot { it.isVerifiedOnDisk }
                        .map { it.fileName }
                        .toSet()

                val fileSpecs = pack.members
                    .filter { it.asset.metadata.localFileName in missingFileNames }
                    .map { member ->
                        val metadata = member.asset.metadata
                        DownloadFileSpec(
                            remoteFileName = metadata.remoteFileName,
                            localFileName = metadata.localFileName,
                            sha256 = metadata.sha256,
                            sizeInBytes = metadata.sizeInBytes,
                            huggingFaceModelName = metadata.huggingFaceModelName,
                            huggingFacePath = metadata.huggingFacePath,
                            source = metadata.source.name,
                            modelFileFormat = metadata.modelFileFormat.name,
                            utilityType = metadata.utilityType?.name,
                        )
                    }

                if (fileSpecs.isEmpty()) {
                    emptyList()
                } else {
                    val sessionId = UUID.randomUUID().toString()
                    val request =
                        DownloadWorkRequest(
                            files = fileSpecs,
                            sessionId = sessionId,
                            requestKind = DownloadRequestKind.INITIALIZE_MODELS,
                            targetModelId = null,
                            wifiOnly = true,
                        )
                    downloadWorkScheduler.enqueue(request)
                    listOf(
                        ScheduledDownload(
                            sessionId = sessionId,
                            requestKind = DownloadRequestKind.INITIALIZE_MODELS,
                        )
                    )
                }
            }
    }
