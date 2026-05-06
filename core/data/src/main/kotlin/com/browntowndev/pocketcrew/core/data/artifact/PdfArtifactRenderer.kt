package com.browntowndev.pocketcrew.core.data.artifact

import android.content.Context
import android.os.Environment
import android.print.PrintAttributes
import android.webkit.WebView
import android.webkit.WebViewClient
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Renders an ArtifactGenerationResult into a professional PDF file using a headless WebView.
 * Converts Markdown to HTML and then prints to PDF.
 */
@Singleton
class PdfArtifactRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val extensions = listOf(TablesExtension.create())
    private val markdownParser = Parser.builder()
        .extensions(extensions)
        .build()
    private val htmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .build()

    companion object {
        private const val PDF_DPI = 300
        private const val PDF_MARGIN_DPI = 150 // 0.15 inch margins

        private var isSlowWholeDocumentDrawEnabled = false

        private fun ensureSlowWholeDocumentDrawEnabled() {
            if (!isSlowWholeDocumentDrawEnabled) {
                try {
                    WebView.enableSlowWholeDocumentDraw()
                    isSlowWholeDocumentDrawEnabled = true
                } catch (_: Throwable) {
                    // Ignore failures in unit tests
                }
            }
        }
    }

    /**
     * Renders the artifact to a PDF file and returns the File handle.
     */
    suspend fun renderToPdf(result: ArtifactGenerationResult, fileName: String? = null): File {
        ensureSlowWholeDocumentDrawEnabled()
        val safeFileName = fileName ?: "Artifact_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), safeFileName)

        val htmlContent = convertMarkdownToHtml(result.request.title, result.request.content)

        return withContext(Dispatchers.Main) {
            renderHtmlToPdf(htmlContent, file)
        }
    }

    internal fun convertMarkdownToHtml(title: String, markdown: String): String {
        val document = markdownParser.parse(markdown)
        val bodyHtml = htmlRenderer.render(document)

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body {
                        font-family: sans-serif;
                        padding: 0;
                        margin: 0;
                        line-height: 1.6;
                        color: #333;
                    }
                    h1 { color: #000; font-size: 28px; margin-bottom: 20px; }
                    h2 { color: #222; font-size: 22px; margin-top: 30px; border-bottom: 1px solid #eee; padding-bottom: 5px; }
                    h3 { color: #444; font-size: 18px; margin-top: 20px; }
                    p { margin-bottom: 15px; font-size: 14px; }
                    ul, ol { margin-bottom: 15px; padding-left: 25px; }
                    li { margin-bottom: 8px; font-size: 14px; }
                    code { background: #f4f4f4; padding: 2px 4px; border-radius: 4px; font-family: monospace; font-size: 13px; }
                    pre { background: #f4f4f4; padding: 15px; border-radius: 8px; overflow-x: auto; margin: 20px 0; border: 1px solid #ddd; }
                    pre code { background: transparent; padding: 0; font-size: 13px; }
                    table { border-collapse: collapse; width: 100%; margin: 20px 0; }
                    th, td { border: 1px solid #ddd; padding: 10px; text-align: left; font-size: 13px; }
                    th { background-color: #f8f8f8; font-weight: bold; }
                    blockquote { border-left: 4px solid #ccc; margin: 0; padding-left: 16px; color: #666; font-style: italic; }
                </style>
            </head>
            <body>
                <h1>$title</h1>
                $bodyHtml
            </body>
            </html>
        """.trimIndent()
    }

    private suspend fun renderHtmlToPdf(html: String, destinationFile: File): File = suspendCancellableCoroutine { continuation ->
        val webView = WebView(context)
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val printAdapter = view.createPrintDocumentAdapter("Artifact")
                val printAttributes = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
                    .setResolution(PrintAttributes.Resolution("pdf", "pdf", PDF_DPI, PDF_DPI))
                    .setMinMargins(
                        PrintAttributes.Margins(
                            PDF_MARGIN_DPI, PDF_MARGIN_DPI,
                            PDF_MARGIN_DPI, PDF_MARGIN_DPI
                        )
                    ) // 0.15 inch margins
                    .build()

                val pdfPrinter = android.print.PdfPrinter(printAttributes)
                pdfPrinter.generatePdf(printAdapter, destinationFile, object : android.print.PdfPrinter.Callback {
                    override fun onSuccess(file: File, pageCount: Int) {
                        continuation.resume(file)
                    }

                    override fun onFailure(error: String) {
                        continuation.resumeWithException(RuntimeException(error))
                    }

                    override fun onCancelled() {
                        continuation.cancel()
                    }
                })
            }
        }

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        
        continuation.invokeOnCancellation {
            webView.stopLoading()
        }
    }
}
