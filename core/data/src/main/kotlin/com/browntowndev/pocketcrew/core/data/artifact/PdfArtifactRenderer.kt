package com.browntowndev.pocketcrew.core.data.artifact

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactBlock
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationResult
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactSection
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders an ArtifactGenerationResult into a professional PDF file using Android's native PdfDocument API.
 * This is the core on-device implementation for Phase 1.
 */
@Singleton
class PdfArtifactRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val pageWidth = 595 // A4 width in points
    private val pageHeight = 842 // A4 height in points
    private val margin = 50f

    private val titlePaint = Paint().apply {
        textSize = 22f
        isFakeBoldText = true
        color = android.graphics.Color.BLACK
    }

    private val headingPaint = Paint().apply {
        textSize = 16f
        isFakeBoldText = true
        color = android.graphics.Color.BLACK
    }

    private val bodyPaint = Paint().apply {
        textSize = 11f
        color = android.graphics.Color.BLACK
    }

    private val listPaint = Paint().apply {
        textSize = 11f
        color = android.graphics.Color.BLACK
    }

    /**
     * Renders the artifact to a PDF file and returns the File handle.
     */
    fun renderToPdf(result: ArtifactGenerationResult, fileName: String? = null): File {
        val document = PdfDocument()
        var currentPage = 1
        var yPosition = margin

        val safeFileName = fileName ?: "Artifact_${System.currentTimeMillis()}.pdf"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), safeFileName)

        // Create first page
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPage).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        
        // Ensure white background
        canvas.drawColor(android.graphics.Color.WHITE)

        // Title
        yPosition = drawText(canvas, result.request.title, margin, yPosition, titlePaint, pageWidth - 2 * margin)
        yPosition += 20f

        result.request.sections.forEach { section ->
            // Check if we need a new page
            if (yPosition > pageHeight - margin - 100) {
                document.finishPage(page)
                currentPage++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPage).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                canvas.drawColor(android.graphics.Color.WHITE)
                yPosition = margin
            }

            // Section Title
            yPosition = drawText(canvas, section.title, margin, yPosition, headingPaint, pageWidth - 2 * margin)
            yPosition += 12f

            section.blocks.forEach { block ->
                if (yPosition > pageHeight - margin - 80) {
                    document.finishPage(page)
                    currentPage++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPage).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    canvas.drawColor(android.graphics.Color.WHITE)
                    yPosition = margin
                }

                when (block) {
                    is ArtifactBlock.Heading -> {
                        yPosition = drawText(canvas, block.text, margin, yPosition, headingPaint, pageWidth - 2 * margin)
                    }
                    is ArtifactBlock.Paragraph -> {
                        yPosition = drawText(canvas, block.text, margin, yPosition, bodyPaint, pageWidth - 2 * margin)
                    }
                    is ArtifactBlock.BulletList -> {
                        block.items.forEach { item ->
                            yPosition = drawText(canvas, "• $item", margin + 10, yPosition, listPaint, pageWidth - 2 * margin - 20)
                        }
                    }
                    is ArtifactBlock.NumberedList -> {
                        block.items.forEachIndexed { index, item ->
                            yPosition = drawText(canvas, "${index + 1}. $item", margin + 10, yPosition, listPaint, pageWidth - 2 * margin - 20)
                        }
                    }
                    is ArtifactBlock.Table -> {
                        // Simple table rendering (basic for Phase 1)
                        yPosition = drawTable(canvas, block, margin, yPosition, pageWidth - 2 * margin)
                    }
                    is ArtifactBlock.CodeBlock -> {
                        yPosition = drawText(canvas, "[Code: ${block.language ?: "text"}]", margin, yPosition, bodyPaint, pageWidth - 2 * margin)
                        yPosition = drawText(canvas, block.code, margin + 10, yPosition, bodyPaint, pageWidth - 2 * margin - 20)
                    }
                }
                yPosition += 8f
            }
            yPosition += 15f // Space between sections
        }

        document.finishPage(page)
        document.writeTo(FileOutputStream(file))
        document.close()

        return file
    }

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, maxWidth: Float): Float {
        val lines = text.split("\n")
        var currentY = y

        for (line in lines) {
            val words = line.split(" ")
            var currentLine = ""

            for (word in words) {
                if (word.isEmpty()) continue
                
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(testLine) < maxWidth) {
                    currentLine = testLine
                } else {
                    if (currentLine.isNotEmpty()) {
                        canvas.drawText(currentLine, x, currentY, paint)
                        currentY += paint.textSize + 6 // Increased line spacing
                    }
                    currentLine = word
                }
            }
            if (currentLine.isNotEmpty()) {
                canvas.drawText(currentLine, x, currentY, paint)
                currentY += paint.textSize + 6
            } else if (line.isEmpty()) {
                // Handle empty lines for spacing
                currentY += paint.textSize + 6
            }
        }
        return currentY
    }

    private fun drawTable(canvas: Canvas, table: ArtifactBlock.Table, x: Float, y: Float, width: Float): Float {
        var currentY = y
        val colWidth = width / table.headers.size

        // Draw header
        table.headers.forEachIndexed { index, header ->
            canvas.drawText(header, x + (index * colWidth) + 5, currentY, headingPaint)
        }
        currentY += 20f

        // Draw rows
        table.rows.forEach { row ->
            row.forEachIndexed { index, cell ->
                if (index < table.headers.size) {
                    canvas.drawText(cell, x + (index * colWidth) + 5, currentY, bodyPaint)
                }
            }
            currentY += 18f
        }
        return currentY
    }
}
