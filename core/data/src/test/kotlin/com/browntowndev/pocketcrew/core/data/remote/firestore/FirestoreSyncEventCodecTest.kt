package com.browntowndev.pocketcrew.core.data.remote.firestore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FirestoreSyncEventCodecTest {
    @Test
    fun `SCN-ANDROID-002 malformed event fails with exact exception message`() {
        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                FirestoreSyncEventCodec.parse(mapOf("schemaVersion" to 99))
            }

        assertEquals("unsupported sync event schema version: 99", exception.message)
    }
}
