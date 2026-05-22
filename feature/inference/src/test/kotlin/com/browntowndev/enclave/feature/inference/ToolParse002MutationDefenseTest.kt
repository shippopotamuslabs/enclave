package com.browntowndev.enclave.feature.inference

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

/**
 * TOOL-PARSE-002 K-05, MD-03, MD-04 — mutation defense (must fail until Phase D migration).
 */
class ToolParse002MutationDefenseTest {
    private val inferenceMainRoot: Path =
        Path.of(
            "src/main/kotlin/com/browntowndev/enclave/feature/inference",
        )

    @Test
    fun md03AgentTurnTextGateSourceAbsent() {
        val gateFile = inferenceMainRoot.resolve("AgentTurnTextGate.kt")
        assertFalse(
            Files.exists(gateFile),
            "MD-03: AgentTurnTextGate.kt must be deleted after migration",
        )
        val sources =
            Files
                .walk(inferenceMainRoot)
                .filter { it.toString().endsWith(".kt") }
                .map { Files.readString(it) }
                .toList()
        assertEquals(
            0,
            sources.count { it.contains("class AgentTurnTextGate") },
            "MD-03: no AgentTurnTextGate class in feature/inference",
        )
    }

    @Test
    fun md04ToolEnvelopeFailureMapperAbsent() {
        val mapperFile = inferenceMainRoot.resolve("ToolEnvelopeFailureMapper.kt")
        assertFalse(
            Files.exists(mapperFile),
            "MD-04: ToolEnvelopeFailureMapper.kt must be deleted",
        )
        val sources =
            Files
                .walk(inferenceMainRoot)
                .filter { it.toString().endsWith(".kt") }
                .toList()
        sources.forEach { path ->
            val text = Files.readString(path)
            assertFalse(
                text.contains("toInferenceException"),
                "MD-04: toInferenceException must not appear in ${path.fileName}",
            )
        }
    }
}
