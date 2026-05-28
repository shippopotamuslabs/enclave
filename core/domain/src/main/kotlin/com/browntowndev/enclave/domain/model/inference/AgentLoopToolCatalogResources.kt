package com.browntowndev.enclave.domain.model.inference

/**
 * Bundled Rust [`ToolSpec`] catalog for the agent core (merged into session semantics at runtime).
 */
object AgentLoopToolCatalogResources {
    val bundledCatalogJson: String by lazy {
        checkNotNull(
            AgentLoopToolCatalogResources::class.java.classLoader
                .getResourceAsStream("agent_loop_tool_catalog.json"),
        ) {
            "agent_loop_tool_catalog.json missing from domain resources"
        }.bufferedReader().use { it.readText() }
    }
}
