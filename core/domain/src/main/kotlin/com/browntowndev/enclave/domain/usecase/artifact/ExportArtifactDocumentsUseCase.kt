@file:Suppress(
    "MaxLineLength",
    "MagicNumber",
    "UnusedPrivateProperty",
    "UnusedParameter",
    "TooManyFunctions",
    "ComplexCondition",
    "LargeClass",
    "CyclomaticComplexMethod",
    "SwallowedException",
    "InstanceOfCheckForException",
    "ThrowsCount",
    "NestedBlockDepth",
    "ReturnCount",
    "LoopWithTooManyJumpStatements",
    "FunctionNaming",
    "LongMethod",
    "MatchingDeclarationName",
    "UnusedPrivateMember",
    "LongParameterList",
)

package com.browntowndev.enclave.domain.usecase.artifact

import com.browntowndev.enclave.domain.model.artifact.ArtifactExportDestination
import com.browntowndev.enclave.domain.model.artifact.ArtifactExportFormat
import com.browntowndev.enclave.domain.model.artifact.ArtifactExportOutcome
import com.browntowndev.enclave.domain.model.artifact.ExportArtifactDocumentsCommand
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.media.ShareDocumentBytesInput
import com.browntowndev.enclave.domain.port.docgen.DocumentGeneratorPort
import com.browntowndev.enclave.domain.port.media.SaveBinaryToDownloadsPort
import com.browntowndev.enclave.domain.port.media.ShareMediaPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

open class ExportArtifactDocumentsUseCase
    @Inject
    constructor(
        private val documentGeneratorPort: DocumentGeneratorPort,
        private val shareMediaPort: ShareMediaPort,
        private val saveBinaryToDownloadsPort: SaveBinaryToDownloadsPort,
    ) {
        open suspend operator fun invoke(command: ExportArtifactDocumentsCommand): Result<ArtifactExportOutcome> =
            try {
                val base = sanitizeBaseFileName(command.baseTitle)
                val json = command.documentJson
                val toShare = mutableListOf<ShareDocumentBytesInput>()
                val downloadedNames = mutableListOf<String>()

                suspend fun addPdf() {
                    val bytes =
                        withContext(Dispatchers.IO) {
                            documentGeneratorPort.generatePdfDocument(json).getOrThrow()
                        }
                    val name = "$base.pdf"
                    when (command.destination) {
                        ArtifactExportDestination.SHARE -> {
                            toShare.add(
                                ShareDocumentBytesInput(
                                    suggestedFileName = name,
                                    mimeType = MimeType.PDF.value,
                                    bytes = bytes,
                                ),
                            )
                        }
                        ArtifactExportDestination.DOWNLOAD -> {
                            saveBinaryToDownloadsPort.save(bytes, name, MimeType.PDF.value).getOrThrow()
                            downloadedNames.add(name)
                        }
                    }
                }

                suspend fun addWord() {
                    val bytes =
                        withContext(Dispatchers.IO) {
                            documentGeneratorPort.generateWordDocument(json).getOrThrow()
                        }
                    val name = "$base.docx"
                    when (command.destination) {
                        ArtifactExportDestination.SHARE -> {
                            toShare.add(
                                ShareDocumentBytesInput(
                                    suggestedFileName = name,
                                    mimeType = MimeType.WORD.value,
                                    bytes = bytes,
                                ),
                            )
                        }
                        ArtifactExportDestination.DOWNLOAD -> {
                            saveBinaryToDownloadsPort.save(bytes, name, MimeType.WORD.value).getOrThrow()
                            downloadedNames.add(name)
                        }
                    }
                }

                when (command.format) {
                    ArtifactExportFormat.PDF -> addPdf()
                    ArtifactExportFormat.WORD -> addWord()
                    ArtifactExportFormat.BOTH -> {
                        addPdf()
                        addWord()
                    }
                }

                val outcome =
                    when (command.destination) {
                        ArtifactExportDestination.SHARE -> {
                            shareMediaPort.shareDocumentBytes(toShare)
                            ArtifactExportOutcome.Shared(fileCount = toShare.size)
                        }
                        ArtifactExportDestination.DOWNLOAD -> {
                            ArtifactExportOutcome.Downloaded(savedDisplayNames = downloadedNames.toList())
                        }
                    }
                Result.success(outcome)
            } catch (e: Throwable) {
                Result.failure(e)
            }

        private fun sanitizeBaseFileName(title: String): String =
            title
                .replace("[^a-zA-Z0-9.-]".toRegex(), "_")
                .take(120)
                .trim('.')
                .ifBlank { "artifact" }
    }
