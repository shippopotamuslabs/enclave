package com.browntowndev.enclave.domain.model.inference

/**
 * Host-owned tool semantics JSON for the Rust agent core (catalog tools merged from session IO).
 */
object AgentLoopToolSemanticsResources {
    val enclaveDefaultJson: String by lazy {
        checkNotNull(
            AgentLoopToolSemanticsResources::class.java.classLoader
                .getResourceAsStream("agent_loop_enclave_tool_semantics.json"),
        ) {
            "agent_loop_enclave_tool_semantics.json missing from domain resources"
        }.bufferedReader().use { it.readText() }
    }
}
