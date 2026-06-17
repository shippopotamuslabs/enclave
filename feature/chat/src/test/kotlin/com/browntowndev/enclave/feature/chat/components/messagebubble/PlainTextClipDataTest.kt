package com.browntowndev.enclave.feature.chat.components.messagebubble

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlainTextClipDataTest {
    @Test
    fun plainTextClipData_usesStableLabel() {
        val clipData = plainTextClipData("hello")

        assertEquals(PLAIN_TEXT_CLIP_LABEL, clipData.description.label.toString())
        assertEquals("hello", clipData.getItemAt(0).text.toString())
    }
}
