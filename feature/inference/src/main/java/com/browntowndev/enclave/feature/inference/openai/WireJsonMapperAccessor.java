package com.browntowndev.enclave.feature.inference.openai;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.openai.core.ObjectMappers;

/**
 * Java bridge to the OpenAI SDK's internal {@link ObjectMappers#jsonMapper()}.
 * <p>
 * {@code ObjectMappers} is {@code internal} in the SDK's Kotlin source, so it is invisible to the
 * Kotlin compiler from another module. Java does not enforce Kotlin {@code internal} visibility,
 * so this accessor exposes the SDK's exact wire {@link JsonMapper} — the same mapper the SDK uses
 * to serialize request bodies to HTTP. Using it for diagnostics guarantees byte-identical field
 * ordering and inclusion rules as the actual wire format.
 */
public final class WireJsonMapperAccessor {
    private static final JsonMapper WIRE_MAPPER = ObjectMappers.jsonMapper();

    private WireJsonMapperAccessor() {}

    public static JsonMapper wireJsonMapper() {
        return WIRE_MAPPER;
    }
}
