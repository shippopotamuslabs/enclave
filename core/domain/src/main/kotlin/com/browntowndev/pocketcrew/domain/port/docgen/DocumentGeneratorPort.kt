package com.browntowndev.pocketcrew.domain.port.docgen

/**
 * Port for generating documents locally using the Rust library.
 */
interface DocumentGeneratorPort {
    /**
     * Generates a Word (DOCX) document.
     * 
     * @param json The structured document JSON.
     * @return A Result containing the raw bytes of the generated DOCX.
     */
    suspend fun generateWordDocument(json: String): Result<ByteArray>

    /**
     * Generates a PDF document.
     * 
     * @param json The structured document JSON.
     * @return A Result containing the raw bytes of the generated PDF.
     */
    suspend fun generatePdfDocument(json: String): Result<ByteArray>
}
