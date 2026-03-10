package com.browntowndev.pocketcrew.domain.port.inference

/**
 * Complexity tiers for prompt routing.
 * Maps to different model capabilities:
 *
 * - SIMPLE: Fast on-device models (e.g., Gemini Nano, Phi-3 Mini)
 * - MEDIUM: Mid-range models (e.g., Gemma-2B, Phi-3)
 * - COMPLEX: Capable models (e.g., Gemini 1.5 Flash, Claude 3 Haiku)
 * - REASONING: Large reasoning models (e.g., Gemini 1.5 Pro, Claude 3.5 Sonnet)
 */
enum class ComplexityLevel {
    /** Simple prompts - basic factual recall, simple lists, short creative writing */
    SIMPLE,

    /** Medium complexity - moderate reasoning, structured output, some analysis */
    MEDIUM,

    /** Complex prompts - multi-step tasks, technical content, detailed explanations */
    COMPLEX,

    /** Reasoning-intensive - deep analysis, problem-solving, creative synthesis */
    REASONING
}
