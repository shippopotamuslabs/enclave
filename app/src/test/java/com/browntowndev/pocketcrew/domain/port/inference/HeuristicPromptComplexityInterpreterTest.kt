package com.browntowndev.pocketcrew.domain.port.inference

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

/**
 * Comprehensive unit tests for HeuristicPromptComplexityInterpreter.
 * Tests all 14 dimensions and various edge cases.
 */
class HeuristicPromptComplexityInterpreterTest {

    private lateinit var interpreter: HeuristicPromptComplexityInterpreter

    @BeforeEach
    fun setup() {
        interpreter = HeuristicPromptComplexityInterpreter(RouterConfig())
    }

    // ========== BASIC FUNCTIONALITY ==========

    @Test
    fun `analyze returns SIMPLE for empty or whitespace prompts`() {
        assertEquals(ComplexityLevel.SIMPLE, interpreter.analyze(""))
        assertEquals(ComplexityLevel.SIMPLE, interpreter.analyze("   "))
        assertEquals(ComplexityLevel.SIMPLE, interpreter.analyze("\n\t"))
    }

    @Test
    fun `analyze returns valid complexity level for all inputs`() {
        val prompts = listOf(
            "Hello",
            "What is AI?",
            "Write a poem about love",
            "Analyze the market trends",
            "Design a system architecture"
        )
        prompts.forEach { prompt ->
            val result = interpreter.analyze(prompt)
            assertTrue(result.ordinal in 0..3, "Invalid complexity level for: $prompt")
        }
    }

    @Test
    fun `analyzeDetailed provides complete breakdown`() {
        val analysis = interpreter.analyzeDetailed("Analyze the impact of AI on employment")

        assertEquals(ComplexityLevel.REASONING, analysis.complexityLevel)
        assertTrue(analysis.requiresReasoning)
        assertTrue(analysis.charCount > 0)
        assertTrue(analysis.wordCount > 0)
        assertTrue(analysis.dimensionScores.isNotEmpty())
        assertTrue(analysis.totalScore in 0..100)
    }

    @Test
    fun `analyzeDetailed returns empty for blank input`() {
        val analysis = interpreter.analyzeDetailed("")
        assertEquals(ComplexityLevel.SIMPLE, analysis.complexityLevel)
        assertFalse(analysis.requiresReasoning)
    }

    // ========== CODE BLOCK DETECTION ==========

    @Test
    fun `analyze detects code blocks with triple backticks`() {
        val prompt = """
            Write a function in Python:
            ```python
            def hello():
                print("world")
            ```
        """.trimIndent()

        val result = interpreter.analyze(prompt)
        assertTrue(result.ordinal >= ComplexityLevel.MEDIUM.ordinal)
    }

    @Test
    fun `analyze detects inline code with backticks`() {
        val prompt = "Use the `List.map()` function to transform data"
        val result = interpreter.analyze(prompt)
        assertTrue(result.ordinal >= ComplexityLevel.MEDIUM.ordinal)
    }

    @Test
    fun `analyze detects JSON blocks`() {
        val prompt = """
            Return this JSON:
            {"name": "test", "value": 42}
        """.trimIndent()

        val result = interpreter.analyze(prompt)
        assertTrue(result.ordinal >= ComplexityLevel.MEDIUM.ordinal)
    }

    // ========== MATH/SYMBOL DETECTION ==========

    @Test
    fun `analyze detects mathematical symbols`() {
        val prompts = listOf(
            "Calculate x + y = 5 where x = 2",
            "What is √144?",
            "Solve for x: 2x + 3 = 7"
        )

        prompts.forEach { prompt ->
            val result = interpreter.analyze(prompt)
            assertTrue(result.ordinal >= ComplexityLevel.MEDIUM.ordinal, "Failed for: $prompt")
        }
    }

    @Test
    fun `analyze detects math keywords`() {
        val prompt = "Calculate the derivative of f(x) = x^2 + 3x - 5"
        val result = interpreter.analyze(prompt)
        assertTrue(result.ordinal >= ComplexityLevel.MEDIUM.ordinal)
    }

    @Test
    fun `analyze detects Greek letters`() {
        val prompt = "What is the value of π? How about θ?"
        val result = interpreter.analyze(prompt)
        assertTrue(result.ordinal >= ComplexityLevel.MEDIUM.ordinal)
    }

    // ========== REASONING/MULTI-STEP KEYWORDS ==========

    @Test
    fun `analyze detects reasoning keywords`() {
        val prompts = listOf(
            "Analyze the pros and cons",
            "Compare and contrast",
            "Evaluate this approach",
            "Explain why this works",
            "Step by step solve this"
        )

        prompts.forEach { prompt ->
            val result = interpreter.analyze(prompt)
            assertTrue(result.ordinal >= ComplexityLevel.COMPLEX.ordinal, "Failed for: $prompt")
        }
    }

    @Test
    fun `analyze detects debugging and refactoring keywords`() {
        val prompts = listOf(
            "Debug this code and find the bug",
            "Refactor this function for better performance",
            "Optimize the algorithm"
        )

        prompts.forEach { prompt ->
            val result = interpreter.analyze(prompt)
            assertTrue(result.ordinal >= ComplexityLevel.COMPLEX.ordinal)
        }
    }

    // ========== LENGTH + DENSITY ==========

    @Test
    fun `analyze considers length in scoring`() {
        val shortPrompt = "Hello"
        val longPrompt = "A".repeat(2000)

        val shortResult = interpreter.analyze(shortPrompt)
        val longResult = interpreter.analyze(longPrompt)

        assertTrue(longResult.ordinal >= shortResult.ordinal)
    }

    // ========== STRUCTURED OUTPUT REQUESTS ==========

    @Test
    fun `analyze detects JSON format requests`() {
        val prompts = listOf(
            "Return the data as JSON",
            "Output in yaml format",
            "Format as XML"
        )

        prompts.forEach { prompt ->
            val result = interpreter.analyze(prompt)
            assertTrue(result.ordinal >= ComplexityLevel.MEDIUM.ordinal)
        }
    }

    @Test
    fun `analyze detects table and list requests`() {
        val prompts = listOf(
            "Show the results in a table",
            "List all items",
            "Present as markdown"
        )

        prompts.forEach { prompt ->
            val result = interpreter.analyze(prompt)
            assertTrue(result.ordinal >= ComplexityLevel.MEDIUM.ordinal)
        }
    }

    // ========== QUESTION COMPLEXITY ==========

    @Test
    fun `analyze detects complex question patterns`() {
        val prompts = listOf(
            "What if the world ran out of oil?",
            "Prove that this theorem is true",
            "Design a system for autonomous vehicles",
            "Explain how neural networks learn"
        )

        prompts.forEach { prompt ->
            val result = interpreter.analyze(prompt)
            assertTrue(result.ordinal >= ComplexityLevel.COMPLEX.ordinal, "Failed for: $prompt")
        }
    }

    // ========== TECHNICAL TERMS ==========

    @Test
    fun `analyze detects programming terms`() {
        val prompt = "Implement an API endpoint with REST and OAuth2 authentication"
        val result = interpreter.analyze(prompt)
        assertTrue(result.ordinal >= ComplexityLevel.COMPLEX.ordinal)
    }

    @Test
    fun `analyze detects machine learning terms`() {
        val prompt = "Explain how the transformer model works in deep learning"
        val result = interpreter.analyze(prompt)
        assertTrue(result.ordinal >= ComplexityLevel.COMPLEX.ordinal)
    }

    // ========== AGENTIC/TOOL SIGNALS ==========

    @Test
    fun `analyze detects tool usage keywords`() {
        val prompts = listOf(
            "Use the search API to find information",
            "Call the function to process this",
            "Execute the database query"
        )

        prompts.forEach { prompt ->
            val result = interpreter.analyze(prompt)
            assertTrue(result.ordinal >= ComplexityLevel.MEDIUM.ordinal)
        }
    }

    // ========== NEGATION PATTERNS ==========

    @Test
    fun `analyze detects negation patterns`() {
        val prompts = listOf(
            "What are the advantages but also the disadvantages?",
            "This vs that - what's better?",
            "However, I disagree because"
        )

        prompts.forEach { prompt ->
            val result = interpreter.analyze(prompt)
            assertTrue(result.ordinal >= ComplexityLevel.MEDIUM.ordinal)
        }
    }

    // ========== CAUSAL LANGUAGE ==========

    @Test
    fun `analyze detects causal language`() {
        val prompts = listOf(
            "Why did this happen? Therefore, we should...",
            "Because of the weather, the event was cancelled",
            "This leads to consequences as a result"
        )

        prompts.forEach { prompt ->
            val result = interpreter.analyze(prompt)
            assertTrue(result.ordinal >= ComplexityLevel.MEDIUM.ordinal)
        }
    }

    // ========== HYPOTHETICAL/CONDITIONAL ==========

    @Test
    fun `analyze detects hypothetical patterns`() {
        val prompts = listOf(
            "What would happen if gravity stopped?",
            "If it rains tomorrow, then we should",
            "Suppose we had unlimited resources"
        )

        prompts.forEach { prompt ->
            val result = interpreter.analyze(prompt)
            assertTrue(result.ordinal >= ComplexityLevel.COMPLEX.ordinal)
        }
    }

    // ========== LISTS AND TABLES ==========

    @Test
    fun `analyze detects list patterns`() {
        val prompt = """
            First, gather the requirements.
            Second, design the system.
            Third, implement the code.
            Finally, test everything.
        """.trimIndent()

        val result = interpreter.analyze(prompt)
        assertTrue(result.ordinal >= ComplexityLevel.MEDIUM.ordinal)
    }

    @Test
    fun `analyze detects markdown tables`() {
        val prompt = """
            | Name | Age |
            |------|-----|
            | John | 25  |
        """.trimIndent()

        val result = interpreter.analyze(prompt)
        assertTrue(result.ordinal >= ComplexityLevel.MEDIUM.ordinal)
    }

    // ========== EDGE CASES ==========

    @Test
    fun `analyze handles unicode and emojis`() {
        val prompt = "Hello! 😊 How are you? 🎉"
        val result = interpreter.analyze(prompt)
        // Should not crash - just return a valid level
        assertTrue(result.ordinal in 0..3)
    }

    @Test
    fun `analyze handles non-english text`() {
        val prompt = "你好世界" // Chinese
        val result = interpreter.analyze(prompt)
        // Should not crash - just return a valid level
        assertTrue(result.ordinal in 0..3)
    }

    @Test
    fun `analyze handles very long prompts efficiently`() {
        val prompt = "Analyze this. ".repeat(1000) // 16000 chars
        val result = interpreter.analyze(prompt)
        assertTrue(result.ordinal >= ComplexityLevel.COMPLEX.ordinal)
    }

    @Test
    fun `analyzeDetailed shows all dimension scores for complex prompt`() {
        val prompt = """
            Write a function to reverse a linked list in Python.
            Then analyze the time complexity.
            Compare it with an array-based approach.
            Optimize the solution.
            Return the result as JSON.
        """.trimIndent()

        val analysis = interpreter.analyzeDetailed(prompt)

        // Should have some scores for multiple dimensions
        assertTrue(analysis.dimensionScores.containsKey("code_blocks"))
        assertTrue(analysis.dimensionScores.containsKey("reasoning_keywords"))
        assertTrue(analysis.dimensionScores.containsKey("structured_output"))
        assertTrue(analysis.dimensionScores.containsKey("length_density"))
    }

    // ========== REGRESSION TESTS ==========

    @Test
    fun `analyze handles simple greeting`() {
        // Short greeting should be at most MEDIUM
        assertTrue(interpreter.analyze("Hi").ordinal <= ComplexityLevel.MEDIUM.ordinal)
    }

    @Test
    fun `analyze handles short question`() {
        val result = interpreter.analyze("What's the weather?")
        assertTrue(result.ordinal <= ComplexityLevel.MEDIUM.ordinal)
    }

    @Test
    fun `analyze handles creative writing request`() {
        val result = interpreter.analyze("Write a short story about a robot")
        // Creative writing could be anywhere from SIMPLE to COMPLEX
        assertTrue(result.ordinal in 0..3)
    }

    @Test
    fun `analyze correctly identifies reasoning-required prompts`() {
        val reasoningPrompts = listOf(
            "Analyze the economic impact of AI on jobs",
            "Step by step, how would you design a bridge?",
            "Compare the advantages and disadvantages of nuclear power",
            "What are the philosophical implications of free will?"
        )

        reasoningPrompts.forEach { prompt ->
            val result = interpreter.analyze(prompt)
            assertEquals(ComplexityLevel.REASONING, result, "Failed for: $prompt")
        }
    }
}
