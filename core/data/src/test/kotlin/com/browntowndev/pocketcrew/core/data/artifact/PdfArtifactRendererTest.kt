package com.browntowndev.pocketcrew.core.data.artifact

import android.content.Context
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("PdfArtifactRenderer Markdown to HTML Conversion")
class PdfArtifactRendererTest {

    private lateinit var renderer: PdfArtifactRenderer
    private val mockContext = mockk<Context>()

    @BeforeEach
    fun setUp() {
        renderer = PdfArtifactRenderer(mockContext)
    }

    @Test
    @DisplayName("Converts basic headers and paragraphs")
    fun `converts basic markdown`() {
        val title = "Test Document"
        val markdown = """
            # Header 1
            ## Header 2
            
            This is a paragraph with **bold** and *italic* text.
        """.trimIndent()

        val html = renderer.convertMarkdownToHtml(title, markdown)

        assertTrue(html.contains("<h1>Test Document</h1>"), "Title should be in H1")
        assertTrue(html.contains("<h1>Header 1</h1>"), "Header 1 should be converted")
        assertTrue(html.contains("<h2>Header 2</h2>"), "Header 2 should be converted")
        assertTrue(html.contains("<strong>bold</strong>"), "Bold text should be converted")
        assertTrue(html.contains("<em>italic</em>"), "Italic text should be converted")
    }

    @Test
    @DisplayName("Converts lists correctly")
    fun `converts lists`() {
        val markdown = """
            - Bullet 1
            - Bullet 2
            
            1. Numbered 1
            2. Numbered 2
        """.trimIndent()

        val html = renderer.convertMarkdownToHtml("Title", markdown)

        assertTrue(html.contains("<ul>"), "Should contain unordered list")
        assertTrue(html.contains("<li>Bullet 1</li>"), "Bullet 1 should be converted")
        assertTrue(html.contains("<ol>"), "Should contain ordered list")
        assertTrue(html.contains("<li>Numbered 1</li>"), "Numbered 1 should be converted")
    }

    @Test
    @DisplayName("Converts tables correctly")
    fun `converts tables`() {
        val markdown = """
            | Header 1 | Header 2 |
            |----------|----------|
            | Cell 1   | Cell 2   |
            | Cell 3   | Cell 4   |
        """.trimIndent()

        val html = renderer.convertMarkdownToHtml("Title", markdown)

        assertTrue(html.contains("<table>"), "Should contain table tag")
        assertTrue(html.contains("<thead>"), "Should contain table head")
        assertTrue(html.contains("<th>Header 1</th>"), "Should contain table header")
        assertTrue(html.contains("<td>Cell 1</td>"), "Should contain table cell")
    }

    @Test
    @DisplayName("Converts code blocks correctly")
    fun `converts code blocks`() {
        val markdown = """
            Inline `code` here.
            
            ```kotlin
            fun main() {
                println("Hello")
            }
            ```
        """.trimIndent()

        val html = renderer.convertMarkdownToHtml("Title", markdown)

        assertTrue(html.contains("<code>code</code>"), "Inline code should be converted")
        assertTrue(html.contains("<pre><code class=\"language-kotlin\">"), "Code block should have language class")
        assertTrue(html.contains("println(&quot;Hello&quot;)"), "Code block content should be escaped")
    }

    @Test
    @DisplayName("Converts blockquotes and links")
    fun `converts quotes and links`() {
        val markdown = """
            > This is a quote.
            
            [Google](https://google.com)
            
            ![Alt Text](https://example.com/image.png)
        """.trimIndent()

        val html = renderer.convertMarkdownToHtml("Title", markdown)

        assertTrue(html.contains("<blockquote>"), "Should contain blockquote")
        assertTrue(html.contains("<a href=\"https://google.com\">Google</a>"), "Link should be converted")
        assertTrue(html.contains("<img src=\"https://example.com/image.png\" alt=\"Alt Text\" />"), "Image should be converted")
    }

    @Test
    @DisplayName("Applies CSS styling template")
    fun `applies styling`() {
        val html = renderer.convertMarkdownToHtml("Title", "Content")

        assertTrue(html.contains("<style>"), "Should contain CSS style block")
        assertTrue(html.contains("font-family: sans-serif;"), "Should contain base styling")
        assertTrue(html.contains("padding: 0;"), "Should contain 0 padding")
        assertTrue(html.contains("line-height: 1.6;"), "Should contain line height")
    }
}
