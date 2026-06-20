package com.browntowndev.enclave.core.ui.component.markdown

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.sp
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.core.ui.theme.LlmBodyTextStyle
import com.browntowndev.enclave.core.ui.theme.darkMarkdownTheme
import com.browntowndev.enclave.core.ui.theme.lightMarkdownTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that darkMarkdownTheme() is memoised inside StreamableMarkdownText so the
 * Markdown renderer does not receive a fresh MarkdownTheme object on every token-driven
 * recomposition.
 *
 * The test relies on the `themeFactory` seam: production code calls `remember { themeFactory() }`,
 * so the factory is invoked only once per composable lifetime regardless of how many times
 * `isStreaming` or other inputs change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StreamableMarkdownTextRecompositionTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `darkMarkdownTheme applies custom body style when provided`() {
        val custom = LlmBodyTextStyle.copy(fontSize = 18.sp, lineHeight = 29.sp)
        val theme = darkMarkdownTheme(bodyStyle = custom)
        assertEquals(18.sp, theme.bodyStyle.fontSize)
        assertEquals(29.sp, theme.bodyStyle.lineHeight)
    }

    @Test
    fun `darkMarkdownTheme creates a new MarkdownTheme instance on each invocation`() {
        val first = darkMarkdownTheme()
        val second = darkMarkdownTheme()
        assertNotSame(
            "darkMarkdownTheme() allocates a new object each call — remember is required to avoid per-recomposition waste",
            first,
            second,
        )
    }

    @Test
    fun `lightMarkdownTheme applies custom body style when provided`() {
        val custom = LlmBodyTextStyle.copy(fontSize = 18.sp, lineHeight = 29.sp)
        val theme = lightMarkdownTheme(bodyStyle = custom)
        assertEquals(18.sp, theme.bodyStyle.fontSize)
        assertEquals(29.sp, theme.bodyStyle.lineHeight)
    }

    @Test
    fun `lightMarkdownTheme creates a new MarkdownTheme instance on each invocation`() {
        val first = lightMarkdownTheme()
        val second = lightMarkdownTheme()
        assertNotSame(
            "lightMarkdownTheme() allocates a new object each call — remember is required to avoid per-recomposition waste",
            first,
            second,
        )
    }

    @Test
    fun `themeFactory is called only once across multiple recompositions of StreamableMarkdownText`() {
        var factoryCallCount = 0
        val countingFactory =
            MarkdownThemeFactory {
                factoryCallCount++
                darkMarkdownTheme()
            }
        var isStreaming by mutableStateOf(false)

        composeTestRule.setContent {
            EnclaveTheme {
                StreamableMarkdownText(
                    markdown = "Hello world",
                    isStreaming = isStreaming,
                    themeFactory = countingFactory,
                )
            }
        }

        assertEquals("themeFactory must be called exactly once at initial composition", 1, factoryCallCount)

        // Simulate token-by-token isStreaming toggles that drive recomposition during inference.
        composeTestRule.runOnIdle { isStreaming = true }
        composeTestRule.runOnIdle { isStreaming = false }
        composeTestRule.runOnIdle { isStreaming = true }

        assertEquals(
            "themeFactory must not be called again on recomposition — remember { themeFactory() } must be in place",
            1,
            factoryCallCount,
        )
    }
}
