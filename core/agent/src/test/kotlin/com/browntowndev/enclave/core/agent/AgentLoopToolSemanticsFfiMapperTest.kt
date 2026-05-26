package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.inference.AgentLoopAgentConfig
import com.browntowndev.enclave.domain.model.inference.AgentLoopToolSemanticsConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AgentLoopToolSemanticsFfiMapperTest {
    @Test
    fun ts030_domainEnclaveDefaultMapsConfiguredNames() {
        val ffi = AgentLoopAgentConfig().toFfi().toolSemantics

        assertEquals("disclose_tool_schemas", ffi.disclosure?.toolName)
        assertEquals("tool_names", ffi.disclosure?.requestFieldName)
        assertEquals("append_blocks", ffi.artifact?.appendToolName)
        assertEquals("isComplete", ffi.artifact?.completionField)
    }

    @Test
    fun ts031_domainAgnosticConfigMapsEmptySemantics() {
        val ffi =
            AgentLoopAgentConfig(
                toolSemantics = AgentLoopToolSemanticsConfig.agnostic(),
            ).toFfi().toolSemantics

        assertNull(ffi.disclosure)
        assertNull(ffi.artifact)
        assertEquals(0, ffi.greedyExtensionTools.size)
    }
}
