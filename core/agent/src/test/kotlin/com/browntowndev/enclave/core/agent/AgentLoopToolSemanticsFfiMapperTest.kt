package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.inference.AgentLoopAgentConfig
import com.browntowndev.enclave.domain.model.inference.AgentLoopToolSemanticsResources
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentLoopToolSemanticsFfiMapperTest {
    @Test
    fun ts030_domainEnclaveDefaultPassesSemanticsJson() {
        val json = AgentLoopAgentConfig().toFfi().toolSemanticsJson

        assertTrue(json.contains("tool_discovery_tool"))
        assertTrue(json.contains("append_blocks"))
        assertTrue(json.contains("greedy_extension_tools"))
    }

    @Test
    fun ts031_domainAgnosticSemanticsJsonIsMinimal() {
        val json =
            AgentLoopAgentConfig(
                toolSemanticsJson = """{"catalog":{"tools":[]}}""",
            ).toFfi().toolSemanticsJson

        assertFalse(json.contains("tool_discovery_tool"))
        assertFalse(json.contains("append_blocks"))
    }

    @Test
    fun enclaveResourceLoads() {
        assertTrue(AgentLoopToolSemanticsResources.enclaveDefaultJson.contains("start_artifact"))
    }
}
