package com.browntowndev.enclave.domain.model.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AgentLoopToolDiscoverySemanticsTest {
    @Test
    fun `TSD-26 enclave default semantics reference search_tools discovery`() {
        val semantics = AgentLoopToolSemanticsConfig.enclaveDefault()

        assertEquals("search_tools", semantics.discovery?.toolName)
        assertEquals("query", semantics.discovery?.requestFieldName)
    }
}
