package com.browntowndev.pocketcrew.core.ui.component.markdown

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.core.ui.theme.darkMarkdownTheme
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
    fun `themeFactory is called only once across multiple recompositions of StreamableMarkdownText`() {
        var factoryCallCount = 0
        val countingFactory =
            MarkdownThemeFactory {
                factoryCallCount++
                darkMarkdownTheme()
            }
        var isStreaming by mutableStateOf(false)

        composeTestRule.setContent {
            PocketCrewTheme {
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
