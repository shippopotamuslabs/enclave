package com.browntowndev.pocketcrew.domain.usecase.artifact

import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactExportDestination
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactExportFormat
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactExportOutcome
import com.browntowndev.pocketcrew.domain.model.artifact.ExportArtifactDocumentsCommand
import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.domain.model.media.ShareDocumentBytesInput
import com.browntowndev.pocketcrew.domain.port.docgen.DocumentGeneratorPort
import com.browntowndev.pocketcrew.domain.port.media.SaveBinaryToDownloadsPort
import com.browntowndev.pocketcrew.domain.port.media.ShareMediaPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExportArtifactDocumentsUseCaseTest {
    @Test
    fun `share pdf only invokes share with one pdf item`() =
        runTest {
            val pdf = byteArrayOf(1, 2, 3)
            val docGen = FakeDocumentGenerator(pdfBytes = pdf)
            val share = FakeShareMedia()
            val save = FakeSaveDownloads()
            val useCase =
                ExportArtifactDocumentsUseCase(
                    documentGeneratorPort = docGen,
                    shareMediaPort = share,
                    saveBinaryToDownloadsPort = save,
                )
            val cmd =
                ExportArtifactDocumentsCommand(
                    documentJson = """{"blocks":[]}""",
                    baseTitle = "My Doc",
                    format = ArtifactExportFormat.PDF,
                    destination = ArtifactExportDestination.SHARE,
                )
            val result = useCase(cmd)
            assertTrue(result.isSuccess)
            val outcome = result.getOrNull()
            assertInstanceOf(ArtifactExportOutcome.Shared::class.java, outcome)
            assertEquals(1, (outcome as ArtifactExportOutcome.Shared).fileCount)
            assertEquals(1, share.sharedItems.size)
            val items = share.sharedItems.single()
            assertEquals(1, items.size)
            assertEquals("My_Doc.pdf", items.single().suggestedFileName)
            assertEquals(MimeType.PDF.value, items.single().mimeType)
            assertTrue(items.single().bytes.contentEquals(pdf))
            assertTrue(save.saved.isEmpty())
        }

    @Test
    fun `download word saves once with docx mime`() =
        runTest {
            val word = byteArrayOf(9, 9)
            val docGen = FakeDocumentGenerator(wordBytes = word)
            val share = FakeShareMedia()
            val save = FakeSaveDownloads()
            val useCase =
                ExportArtifactDocumentsUseCase(
                    documentGeneratorPort = docGen,
                    shareMediaPort = share,
                    saveBinaryToDownloadsPort = save,
                )
            val cmd =
                ExportArtifactDocumentsCommand(
                    documentJson = "{}",
                    baseTitle = "Report",
                    format = ArtifactExportFormat.WORD,
                    destination = ArtifactExportDestination.DOWNLOAD,
                )
            val result = useCase(cmd)
            assertTrue(result.isSuccess)
            val outcome = result.getOrNull()
            assertInstanceOf(ArtifactExportOutcome.Downloaded::class.java, outcome)
            assertEquals(listOf("Report.docx"), (outcome as ArtifactExportOutcome.Downloaded).savedDisplayNames)
            assertEquals(1, save.saved.size)
            assertEquals("Report.docx", save.saved.single().displayName)
            assertEquals(MimeType.WORD.value, save.saved.single().mimeType)
            assertTrue(
                save.saved
                    .single()
                    .bytes
                    .contentEquals(word),
            )
            assertTrue(share.sharedItems.isEmpty())
        }

    @Test
    fun `share both passes two items with distinct mime types`() =
        runTest {
            val pdf = byteArrayOf(1)
            val word = byteArrayOf(2)
            val docGen = FakeDocumentGenerator(pdfBytes = pdf, wordBytes = word)
            val share = FakeShareMedia()
            val save = FakeSaveDownloads()
            val useCase =
                ExportArtifactDocumentsUseCase(
                    documentGeneratorPort = docGen,
                    shareMediaPort = share,
                    saveBinaryToDownloadsPort = save,
                )
            val cmd =
                ExportArtifactDocumentsCommand(
                    documentJson = "{}",
                    baseTitle = "X",
                    format = ArtifactExportFormat.BOTH,
                    destination = ArtifactExportDestination.SHARE,
                )
            val result = useCase(cmd)
            assertTrue(result.isSuccess)
            assertEquals(2, (result.getOrNull() as ArtifactExportOutcome.Shared).fileCount)
            val items = share.sharedItems.single()
            assertEquals(2, items.size)
            val byName = items.associateBy { it.suggestedFileName }
            assertTrue(byName["X.pdf"]!!.bytes.contentEquals(pdf))
            assertEquals(MimeType.PDF.value, byName["X.pdf"]!!.mimeType)
            assertTrue(byName["X.docx"]!!.bytes.contentEquals(word))
            assertEquals(MimeType.WORD.value, byName["X.docx"]!!.mimeType)
        }

    @Test
    fun `generator failure returns failure`() =
        runTest {
            val docGen =
                FakeDocumentGenerator(
                    pdfBytes = byteArrayOf(),
                    pdfResult = Result.failure(RuntimeException("boom")),
                )
            val useCase =
                ExportArtifactDocumentsUseCase(
                    documentGeneratorPort = docGen,
                    shareMediaPort = FakeShareMedia(),
                    saveBinaryToDownloadsPort = FakeSaveDownloads(),
                )
            val result =
                useCase(
                    ExportArtifactDocumentsCommand(
                        documentJson = "{}",
                        baseTitle = "T",
                        format = ArtifactExportFormat.PDF,
                        destination = ArtifactExportDestination.SHARE,
                    ),
                )
            assertTrue(result.isFailure)
        }

    @Test
    fun `document generation runs off caller thread`() =
        runTest {
            val callerThreadName = Thread.currentThread().name
            val docGen = FakeDocumentGenerator(wordBytes = byteArrayOf(4, 2))
            val useCase =
                ExportArtifactDocumentsUseCase(
                    documentGeneratorPort = docGen,
                    shareMediaPort = FakeShareMedia(),
                    saveBinaryToDownloadsPort = FakeSaveDownloads(),
                )

            val result =
                useCase(
                    ExportArtifactDocumentsCommand(
                        documentJson = "{}",
                        baseTitle = "T",
                        format = ArtifactExportFormat.WORD,
                        destination = ArtifactExportDestination.DOWNLOAD,
                    ),
                )

            assertTrue(result.isSuccess)
            assertNotEquals(callerThreadName, docGen.wordThreadName)
        }

    @Test
    fun `download failure propagates`() =
        runTest {
            val save =
                FakeSaveDownloads(
                    overrideResult = Result.failure(IllegalStateException("no storage")),
                )
            val useCase =
                ExportArtifactDocumentsUseCase(
                    documentGeneratorPort = FakeDocumentGenerator(pdfBytes = byteArrayOf(1)),
                    shareMediaPort = FakeShareMedia(),
                    saveBinaryToDownloadsPort = save,
                )
            val result =
                useCase(
                    ExportArtifactDocumentsCommand(
                        documentJson = "{}",
                        baseTitle = "T",
                        format = ArtifactExportFormat.PDF,
                        destination = ArtifactExportDestination.DOWNLOAD,
                    ),
                )
            assertTrue(result.isFailure)
        }

    private class FakeDocumentGenerator(
        private val wordBytes: ByteArray = byteArrayOf(),
        private val pdfBytes: ByteArray = byteArrayOf(),
        private val wordResult: Result<ByteArray> = Result.success(wordBytes),
        private val pdfResult: Result<ByteArray> = Result.success(pdfBytes),
    ) : DocumentGeneratorPort {
        var wordThreadName: String? = null
            private set

        override suspend fun generateWordDocument(json: String): Result<ByteArray> {
            wordThreadName = Thread.currentThread().name
            return wordResult
        }

        override suspend fun generatePdfDocument(json: String): Result<ByteArray> = pdfResult
    }

    private class FakeShareMedia : ShareMediaPort {
        val sharedItems = mutableListOf<List<ShareDocumentBytesInput>>()

        override fun shareMedia(
            uris: List<String>,
            mimeType: String,
        ) {
            error("not used")
        }

        override fun shareDocumentBytes(items: List<ShareDocumentBytesInput>) {
            sharedItems.add(items.toList())
        }
    }

    private data class Saved(
        val bytes: ByteArray,
        val displayName: String,
        val mimeType: String,
    )

    private class FakeSaveDownloads(
        private val overrideResult: Result<Unit>? = null,
    ) : SaveBinaryToDownloadsPort {
        val saved = mutableListOf<Saved>()

        override suspend fun save(
            bytes: ByteArray,
            displayName: String,
            mimeType: String,
        ): Result<Unit> {
            saved.add(Saved(bytes.copyOf(), displayName, mimeType))
            return overrideResult ?: Result.success(Unit)
        }
    }
}
