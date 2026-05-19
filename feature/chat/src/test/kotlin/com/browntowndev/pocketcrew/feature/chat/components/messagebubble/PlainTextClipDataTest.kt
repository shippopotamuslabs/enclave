package com.browntowndev.pocketcrew.feature.chat.components.messagebubble

import org.junit.Assert.assertEquals
import org.junit.Test

class PlainTextClipDataTest {
    @Test
    fun plainTextClipData_usesStableLabel() {
        val clipData = plainTextClipData("hello")

        assertEquals(PLAIN_TEXT_CLIP_LABEL, clipData.description.toString())
        assertEquals("hello", clipData.getItemAt(0).text.toString())
    }
}
