package com.browntowndev.enclave.core.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EnclaveNeoTokensTest {
    @Test
    fun tokens_useConsistentNeoGeometry() {
        assertEquals(4.dp, EnclaveNeoTokens.cornerRadius)
        assertEquals(2.dp, EnclaveNeoTokens.borderWidth)
        assertEquals(2.dp, EnclaveNeoTokens.buttonShadowOffsetLight)
        assertEquals(5.dp, EnclaveNeoTokens.buttonShadowOffsetDark)
        assertEquals(2.dp, EnclaveNeoTokens.subtleButtonShadowOffset)
        assertEquals(CornerSize(0.dp), EnclaveNeoTokens.flushTopShape.topStart)
        assertEquals(CornerSize(EnclaveNeoTokens.cornerRadius), EnclaveNeoTokens.flushTopShape.bottomEnd)
        assertEquals(4.dp, EnclaveNeoTokens.rowSpacing)
        assertEquals(48.dp, EnclaveNeoTokens.trailingActionSize)
        assertEquals(72.dp, EnclaveNeoTokens.albumThumbnailSize)
    }
}
