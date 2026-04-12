package com.browntowndev.pocketcrew.core.data.media

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.net.URI
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CachedImageAttachmentStorageTest {

    @Test
    fun `stageImage copies attachment bytes without recompression`() = runTest {
        val context = mockk<Context>()
        val sourceFile = File.createTempFile("source-image", ".png")
        val sourceBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        sourceFile.writeBytes(sourceBytes)
        val sourceUri = sourceFile.toURI().toString()

        every { context.filesDir } returns File("/tmp")

        val storage = CachedImageAttachmentStorage(context)
        val stagedUri = storage.stageImage(sourceUri)
        val stagedFile = File(URI(stagedUri))

        assertTrue(stagedFile.exists())
        assertArrayEquals(sourceBytes, stagedFile.readBytes())
    }
}
