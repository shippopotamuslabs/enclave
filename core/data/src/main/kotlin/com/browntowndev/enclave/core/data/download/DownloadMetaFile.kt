package com.browntowndev.enclave.core.data.download

import java.io.File
import java.io.RandomAccessFile

/**
 * Sidecar metadata for in-progress downloads.
 *
 * Format:
 * - line 1: expected size in bytes
 * - line 2: expected SHA-256
 * - line 3 (optional): last fsync-committed byte offset in the temp file
 */
internal object DownloadMetaFile {
    data class Meta(
        val expectedSizeBytes: Long,
        val expectedSha256: String,
        val committedOffset: Long,
        val hasCommittedOffset: Boolean,
    )

    fun write(
        metaFile: File,
        expectedSizeBytes: Long,
        expectedSha256: String,
        committedOffset: Long = 0L,
    ) {
        val content = "$expectedSizeBytes\n$expectedSha256\n$committedOffset"
        val parent = metaFile.parentFile
        if (parent != null) {
            val stagingFile = File(parent, "${metaFile.name}.staging")
            stagingFile.writeText(content)
            if (stagingFile.renameTo(metaFile)) {
                return
            }
            stagingFile.delete()
        }
        metaFile.writeText(content)
    }

    fun updateCommittedOffset(
        metaFile: File,
        committedOffset: Long,
    ) {
        val meta = readOrNull(metaFile) ?: return
        write(
            metaFile = metaFile,
            expectedSizeBytes = meta.expectedSizeBytes,
            expectedSha256 = meta.expectedSha256,
            committedOffset = committedOffset,
        )
    }

    fun readOrNull(metaFile: File): Meta? {
        if (!metaFile.exists()) {
            return null
        }
        return try {
            val lines = metaFile.readLines()
            if (lines.size < 2) {
                return null
            }
            val expectedSize = lines[0].toLongOrNull() ?: return null
            val expectedSha256 = lines[1]
            val hasCommittedOffset = lines.size >= 3
            val committedOffset =
                if (hasCommittedOffset) {
                    lines[2].toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                } else {
                    0L
                }
            Meta(
                expectedSizeBytes = expectedSize,
                expectedSha256 = expectedSha256,
                committedOffset = committedOffset,
                hasCommittedOffset = hasCommittedOffset,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the safe resume offset for a temp file.
     * Legacy meta files without a committed offset fall back to [tempLength].
     */
    fun safeResumeOffset(
        metaFile: File,
        tempLength: Long,
    ): Long {
        val meta = readOrNull(metaFile) ?: return 0L
        val committed = if (meta.hasCommittedOffset) meta.committedOffset else tempLength
        return committed.coerceIn(0L, tempLength)
    }

    fun truncateTempToCommittedOffset(
        tempFile: File,
        committedOffset: Long,
    ) {
        if (!tempFile.exists() || tempFile.length() <= committedOffset) {
            return
        }
        RandomAccessFile(tempFile, "rw").use { it.setLength(committedOffset) }
    }
}
