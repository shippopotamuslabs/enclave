package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.download.DownloadFileSpec
import com.browntowndev.enclave.domain.model.download.DownloadRequestKind
import com.browntowndev.enclave.domain.model.download.DownloadWorkRequest
import com.browntowndev.enclave.domain.model.download.ModelPackMember
import com.browntowndev.enclave.domain.model.download.ScheduledDownload
import com.browntowndev.enclave.domain.port.download.DownloadWorkSchedulerPort
import com.browntowndev.enclave.domain.usecase.download.ResolveModelCatalogUseCase
import com.browntowndev.enclave.domain.usecase.download.VerifyModelPackCompletenessUseCase
import java.util.UUID
import javax.inject.Inject

open class ReDownloadModelPackUseCase
    @Inject
    constructor(
        private val downloadWorkScheduler: DownloadWorkSchedulerPort,
        private val verifyModelPackCompleteness: VerifyModelPackCompletenessUseCase,
        private val resolveModelCatalog: ResolveModelCatalogUseCase,
    ) {
        open suspend operator fun invoke(packId: String): Result<List<ScheduledDownload>> =
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

                val fileSpecs =
                    pack.members
                        .filter { it.asset.metadata.localFileName in missingFileNames }
                        .distinctBy { it.asset.metadata.sha256 }
                        .map { it.toDownloadFileSpec() }

                val specsToEnqueue =
                    fileSpecs.ifEmpty {
                        if (missingFileNames.isEmpty()) {
                            emptyList()
                        } else {
                            pack.members
                                .distinctBy { it.asset.metadata.sha256 }
                                .map { it.toDownloadFileSpec() }
                        }
                    }

                if (specsToEnqueue.isEmpty()) {
                    emptyList()
                } else {
                    enqueuePackDownload(specsToEnqueue)
                }
            }

        private fun enqueuePackDownload(fileSpecs: List<DownloadFileSpec>): List<ScheduledDownload> {
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
            return listOf(
                ScheduledDownload(
                    sessionId = sessionId,
                    requestKind = DownloadRequestKind.INITIALIZE_MODELS,
                ),
            )
        }

        private fun ModelPackMember.toDownloadFileSpec(): DownloadFileSpec {
            val metadata = asset.metadata
            return metadata.toDownloadFileSpec()
        }

        private fun LocalModelMetadata.toDownloadFileSpec(): DownloadFileSpec =
            DownloadFileSpec(
                remoteFileName = remoteFileName,
                localFileName = localFileName,
                sha256 = sha256,
                sizeInBytes = sizeInBytes,
                huggingFaceModelName = huggingFaceModelName,
                huggingFacePath = huggingFacePath,
                source = source.name,
                modelFileFormat = modelFileFormat.name,
                utilityType = utilityType?.name,
            )
    }
