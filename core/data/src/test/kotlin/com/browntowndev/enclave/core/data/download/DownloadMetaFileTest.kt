package com.browntowndev.enclave.core.data.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DownloadMetaFileTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun safeResumeOffset_usesCommittedOffsetAndTruncatesTornWrites() {
        val tempFile = File(tempDir, "model.gguf.tmp")
        val metaFile = File(tempDir, "model.gguf.tmp.meta")
        tempFile.writeBytes(ByteArray(100) { 1 })
        DownloadMetaFile.write(
            metaFile = metaFile,
            expectedSizeBytes = 1_000L,
            expectedSha256 = "abc123",
            committedOffset = 64L,
        )

        val resumeOffset = DownloadMetaFile.safeResumeOffset(metaFile, tempFile.length())
        assertEquals(64L, resumeOffset)

        DownloadMetaFile.truncateTempToCommittedOffset(tempFile, resumeOffset)
        assertEquals(64L, tempFile.length())
    }

    @Test
    fun safeResumeOffset_fallsBackToTempLengthForLegacyMeta() {
        val tempFile = File(tempDir, "legacy.gguf.tmp")
        val metaFile = File(tempDir, "legacy.gguf.tmp.meta")
        tempFile.writeBytes(ByteArray(48) { 2 })
        metaFile.writeText("1000\nlegacy-sha")

        assertEquals(48L, DownloadMetaFile.safeResumeOffset(metaFile, tempFile.length()))
    }

    @Test
    fun readOrNull_rejectsMalformedMeta() {
        val metaFile = File(tempDir, "bad.gguf.tmp.meta")
        metaFile.writeText("not-a-number\nsha")

        assertNull(DownloadMetaFile.readOrNull(metaFile))
    }

    @Test
    fun updateCommittedOffset_preservesExpectedFields() {
        val metaFile = File(tempDir, "model.gguf.tmp.meta")
        DownloadMetaFile.write(metaFile, 500L, "deadbeef", committedOffset = 0L)

        DownloadMetaFile.updateCommittedOffset(metaFile, 128L)

        val meta = DownloadMetaFile.readOrNull(metaFile)
        assertEquals(500L, meta?.expectedSizeBytes)
        assertEquals("deadbeef", meta?.expectedSha256)
        assertEquals(128L, meta?.committedOffset)
        assertTrue(metaFile.readText().lines().size >= 3)
    }
}