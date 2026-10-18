package com.browntowndev.enclave.core.data.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File
import java.net.URI

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class CachedImageAttachmentStorageTest {
    @Test
    fun `stageImage copies attachment bytes without recompression`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val sourceFile = File.createTempFile("source-image", ".png")
            val sourceBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
            sourceFile.writeBytes(sourceBytes)
            val sourceUri = sourceFile.toURI().toString()

            val storage = CachedImageAttachmentStorage(context)
            val stagedUri = storage.stageImage(sourceUri)
            val stagedFile = File(URI(stagedUri))

            assertTrue(stagedFile.exists())
            assertArrayEquals(sourceBytes, stagedFile.readBytes())
        }
}
