package com.browntowndev.enclave.domain.model.inference

/**
 * Wire-format keys shared between the Rust agent engine and Kotlin hosts.
 * Kept in domain so feature/inference production sources avoid embedding literals
 * guarded by agent-core meta tests (MD-02).
 */
object AgentLoopWireKeys {
    const val ENGINE_PAYLOAD_JSON_KEY: String = "_agent"
}
