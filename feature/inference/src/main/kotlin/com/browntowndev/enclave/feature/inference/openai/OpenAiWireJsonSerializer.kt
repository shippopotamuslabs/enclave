package com.browntowndev.enclave.feature.inference.openai

/**
 * Serializes OpenAI SDK request bodies using the SDK's internal wire [JsonMapper].
 *
 * Must match the bytes sent over HTTP — do not substitute a plain [ObjectMapper].
 */
internal object OpenAiWireJsonSerializer {
    private val mapper = WireJsonMapperAccessor.wireJsonMapper()

    fun serializeBody(body: Any): String = mapper.writeValueAsString(body)
}
