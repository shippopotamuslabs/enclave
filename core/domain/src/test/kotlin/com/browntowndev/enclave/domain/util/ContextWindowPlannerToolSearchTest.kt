package com.browntowndev.enclave.domain.util

import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import com.browntowndev.enclave.domain.model.inference.WebSearchParams
import com.browntowndev.enclave.domain.usecase.chat.SearchToolPromptComposer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ContextWindowPlannerToolSearchTest {
    @Test
    fun `TSD-23 ContextWindowPlanner ignores deferred catalog token bulk`() {
        val deferredCatalog =
            (1..10).map { index ->
                ToolCatalogEntry(
                    name = "deferred_$index",
                    description = "x".repeat(50),
                    disclosureMode = ToolDisclosureMode.DISCLOSE_ON_DEMAND,
                )
            }
        val activeTools =
            listOf(
                ToolDefinition.SEARCH_TOOLS,
                makeTool("web_search", "y".repeat(20)),
            )
        val budget =
            ContextWindowPlanner.budgetFor(
                contextWindowTokens = 4096,
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        toolCatalog = deferredCatalog,
                        activeTools = activeTools,
                    ),
                tokenCounter = Tsd23TokenCounter,
            )

        assertEquals(52, budget.toolSchemaTokens)
    }

    private fun makeTool(
        name: String,
        desc: String,
    ) = ToolDefinition(
        name = name,
        description = desc,
        parametersClass = WebSearchParams::class,
    )

    private object Tsd23TokenCounter : TokenCounter {
        override fun countTokens(
            text: String,
            modelName: String?,
        ): Int =
            when {
                text.contains("TOOL CATALOG deferred_1") -> 500
                text == SearchToolPromptComposer.TOOL_DISCOVERY_INSTRUCTION_TEXT -> 12
                text.contains("search_tools") && text.contains("web_search") -> 40
                else -> text.length
            }
    }
}
