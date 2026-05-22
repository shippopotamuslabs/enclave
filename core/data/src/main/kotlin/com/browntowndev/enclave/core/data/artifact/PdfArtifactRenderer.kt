package com.browntowndev.enclave.core.data.artifact

import android.content.Context
import android.os.Environment
import com.browntowndev.enclave.core.docgen.generatePdf
import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders an ArtifactGenerationResult into a professional PDF file using the Rust docgen library.
 */
@Singleton
class PdfArtifactRenderer
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /**
         * Renders the artifact to a PDF file and returns the File handle.
         */
        suspend fun renderToPdf(
            result: ArtifactGenerationResult,
            fileName: String? = null,
        ): File =
            withContext(Dispatchers.IO) {
                val safeFileName = fileName ?: "Artifact_${System.currentTimeMillis()}.pdf"
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), safeFileName)

                val bytes = generatePdf(result.request.content)
                file.writeBytes(bytes)
                file
            }
    }
