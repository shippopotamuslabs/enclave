package com.browntowndev.enclave.domain.model.inference

/**
 * Interface for providing dynamic tool schemas at runtime.
 */
interface DynamicToolSchemaProvider {
    fun getSchema(toolName: String): String?
}
