package com.browntowndev.pocketcrew.feature.inference

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolPromptComposerTest {

    @Test
    fun localToolContract_includesSearchToolByDefault() {
        val contract = ToolPromptComposer.localToolContract(includeImageInspectTool = false)

        assertTrue(contract.contains("tavily_web_search"))
        assertTrue(contract.contains("query"))
        assertFalse(contract.contains("attached_image_inspect"))
    }

    @Test
    fun localToolContract_canIncludeImageInspectTool() {
        val contract = ToolPromptComposer.localToolContract(includeImageInspectTool = true)

        assertTrue(contract.contains("attached_image_inspect"))
        assertTrue(contract.contains("question"))
    }

    @Test
    fun compose_joinsBasePromptAndEnabledToolContract() {
        val prompt = ToolPromptComposer().compose("Be helpful.", includeImageInspectTool = true)

        assertTrue(prompt.contains("Be helpful."))
        assertTrue(prompt.contains("tavily_web_search"))
        assertTrue(prompt.contains("attached_image_inspect"))
    }
}
