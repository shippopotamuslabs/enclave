package com.browntowndev.pocketcrew.feature.inference

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImagePayloadsTest {

    @Test
    fun `fromUris detects png mime type from file bytes when extension is unknown`() {
        val file = File.createTempFile("payload-image", ".img")
        file.writeBytes(
            byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            )
        )

        val payload = ImagePayloads.fromUris(listOf(file.toURI().toString())).single()

        assertEquals("image/png", payload.mimeType)
    }
}
