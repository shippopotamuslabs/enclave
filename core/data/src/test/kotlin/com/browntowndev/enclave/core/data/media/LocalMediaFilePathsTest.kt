package com.browntowndev.enclave.core.data.media

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class LocalMediaFilePathsTest {
    @Test
    fun resolveLocalMediaFile_parsesFileSchemeUri() {
        val file = File.createTempFile("studio", ".jpg")
        file.writeText("bytes")

        val resolved = resolveLocalMediaFile("file://${file.absolutePath}")

        assertEquals(file.absolutePath, resolved.absolutePath)
        file.delete()
    }

    @Test
    fun resolveLocalMediaFile_acceptsBarePath() {
        val file = File.createTempFile("studio", ".mp4")
        file.writeText("bytes")

        val resolved = resolveLocalMediaFile(file.absolutePath)

        assertEquals(file.absolutePath, resolved.absolutePath)
        file.delete()
    }

    @Test
    fun resolveLocalMediaFile_matchesAndroidMediaSaverInputStyle() {
        val file = File.createTempFile("studio", ".jpg")
        file.writeBytes(byteArrayOf(1, 2, 3))

        val saverInput = "file://${file.absolutePath}"
        val resolved = resolveLocalMediaFile(saverInput)

        assertTrue(resolved.exists())
        assertEquals(file.absolutePath, resolved.absolutePath)
        file.delete()
    }
}
