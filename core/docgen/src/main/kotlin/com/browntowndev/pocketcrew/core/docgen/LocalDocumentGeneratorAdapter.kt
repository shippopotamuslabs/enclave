package com.browntowndev.pocketcrew.core.docgen

import android.util.Log
import com.browntowndev.pocketcrew.domain.port.docgen.DocumentGeneratorPort
import javax.inject.Inject

class LocalDocumentGeneratorAdapter @Inject constructor() : DocumentGeneratorPort {
    override suspend fun generateWordDocument(json: String): Result<ByteArray> {
        return try {
            val bytes = com.browntowndev.pocketcrew.core.docgen.generateDocx(json)
            Result.success(bytes)
        } catch (e: Throwable) {
            Log.e("DocGenAdapter", "generateWordDocument failed", e)
            Result.failure(Exception(e.message ?: "Word generation failed", e))
        }
    }

    override suspend fun generatePdfDocument(json: String): Result<ByteArray> {
        return try {
            val bytes = com.browntowndev.pocketcrew.core.docgen.generatePdf(json)
            Result.success(bytes)
        } catch (e: Throwable) {
            Log.e("DocGenAdapter", "generatePdfDocument failed", e)
            Result.failure(Exception(e.message ?: "PDF generation failed", e))
        }
    }
}
