package com.browntowndev.pocketcrew.domain.usecase.chat

enum class ToolCallStrategy {
    /**
     * Native LiteRT 2.x ANTLR grammar.
     * Expects: call:tool_name{arg:"value"}
     */
    LITE_RT_NATIVE,

    /**
     * Standard text-based envelope for custom parsers (llama.cpp, etc).
     * Expects: <tool_call>{"name":"...","arguments":{...}}</tool_call>
     */
    JSON_XML_ENVELOPE,

    /**
     * Cloud APIs that use their own SDK-level tool registration.
     * Often no system prompt instructions are needed as the "tools" field handles it.
     */
    SDK_NATIVE
}
