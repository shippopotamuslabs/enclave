package com.browntowndev.enclave.core.data.media

import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.model.config.MediaCapability
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class AndroidMediaSaverTest {
    private lateinit var saver: AndroidMediaSaver

    @BeforeEach
    fun setUp() {
        saver = AndroidMediaSaver(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun saveToGallery_copiesFileUriImageToMediaStore() =
        runTest {
            val file = File.createTempFile("studio", ".png")
            file.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

            val result = saver.saveToGallery("file://${file.absolutePath}", MediaCapability.IMAGE)

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().startsWith("content://"))
            file.delete()
        }

    @Test
    fun saveToGallery_failsWhenFileUriDoesNotExist() =
        runTest {
            val result =
                saver.saveToGallery(
                    "file:///data/user/0/com.example/files/missing.png",
                    MediaCapability.IMAGE,
                )

            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull()?.message?.contains("Local file not found") == true,
            )
        }
}