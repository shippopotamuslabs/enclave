package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.ImagePayload
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.net.URLConnection
import java.security.MessageDigest

internal data class ImagePayloadSummary(
    val fileName: String,
    val mimeType: String,
    val byteCount: Long,
)

internal object ImagePayloads {
    fun fromUris(imageUris: List<String>): List<ImagePayload> =
        imageUris.map(::fromUri)

    fun summarizeUris(imageUris: List<String>): List<ImagePayloadSummary> =
        imageUris.map(::summarizeUri)

    fun validate(payload: ImagePayload) {
        require(payload.bytes.isNotEmpty()) { "Image payload is empty for ${payload.filename}" }
        require(payload.byteCount > 0) { "Image byte count is zero for ${payload.filename}" }
        require(supportedMimeTypes.contains(payload.mimeType)) {
            "Unsupported MIME type: ${payload.mimeType} for ${payload.filename}. Supported: $supportedMimeTypes"
        }
    }

    /**
     * Dumps the payload to the specified directory for debugging purposes.
     */
    fun dump(payload: ImagePayload, directory: File) {
        runCatching {
            if (!directory.exists()) directory.mkdirs()
            val extension = payload.filename.substringAfterLast(".", "bin")
            val dumpFile = File(directory, "last_image_${payload.sha256.take(8)}.$extension")
            dumpFile.writeBytes(payload.bytes)
            val metadataFile = File(directory, "${dumpFile.name}.metadata.json")
            metadataFile.writeText(
                """
                {
                  "filename": "${payload.filename}",
                  "mimeType": "${payload.mimeType}",
                  "byteCount": ${payload.byteCount},
                  "sha256": "${payload.sha256}",
                  "sourceUri": "${payload.sourceUri}"
                }
                """.trimIndent()
            )
        }
    }

    /**
     * Strips base64 data URI patterns from message content to prevent
     * accidental base64 payload leakage in history turns sent to API providers.
     * The rehydrator already strips images, but this is a defensive guard.
     */
    fun stripBase64DataUris(content: String): String =
        BASE64_DATA_URI_REGEX.replace(content, "[image]")

    private val BASE64_DATA_URI_REGEX = Regex("""data:image/[^;]+;base64,[A-Za-z0-9+/=\s]+""")

    private val supportedMimeTypes = setOf(
        "image/png",
        "image/jpeg",
        "image/webp",
        "image/heic",
        "image/heif",
        "image/gif"
    )

    private fun fromUri(imageUri: String): ImagePayload {
        val file = File(URI(imageUri))
        require(file.exists()) { "Image file does not exist: $imageUri" }
        val bytes = file.readBytes()
        val mimeType = detectMimeType(file)
        return ImagePayload(
            bytes = bytes,
            mimeType = mimeType,
            byteCount = file.length(),
            sourceUri = imageUri,
            filename = file.name,
            sha256 = calculateSha256(bytes)
        )
    }

    private fun summarizeUri(imageUri: String): ImagePayloadSummary {
        val file = File(URI(imageUri))
        require(file.exists()) { "Image file does not exist: $imageUri" }
        return ImagePayloadSummary(
            fileName = file.name,
            mimeType = detectMimeType(file),
            byteCount = file.length(),
        )
    }

    private fun detectMimeType(file: File): String {
        val header = ByteArray(32)
        FileInputStream(file).use { input ->
            val bytesRead = input.read(header)
            detectMimeTypeFromHeader(header, bytesRead)?.let { return it }
        }

        FileInputStream(file).use { input ->
            URLConnection.guessContentTypeFromStream(input)?.let { return it }
        }
        return URLConnection.guessContentTypeFromName(file.name) ?: "image/jpeg"
    }

    private fun detectMimeTypeFromHeader(header: ByteArray, bytesRead: Int): String? {
        if (bytesRead >= 8 &&
            header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte() &&
            header[4] == 0x0D.toByte() &&
            header[5] == 0x0A.toByte() &&
            header[6] == 0x1A.toByte() &&
            header[7] == 0x0A.toByte()
        ) {
            return "image/png"
        }

        if (bytesRead >= 3 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }

        if (bytesRead >= 6) {
            val prefix = runCatching { header.copyOfRange(0, 6).decodeToString() }.getOrNull()
            if (prefix == "GIF87a" || prefix == "GIF89a") {
                return "image/gif"
            }
        }

        if (bytesRead >= 12 &&
            runCatching { header.copyOfRange(0, 4).decodeToString() }.getOrNull() == "RIFF" &&
            runCatching { header.copyOfRange(8, 12).decodeToString() }.getOrNull() == "WEBP"
        ) {
            return "image/webp"
        }

        if (bytesRead >= 12 &&
            runCatching { header.copyOfRange(4, 8).decodeToString() }.getOrNull() == "ftyp"
        ) {
            val brand = runCatching { header.copyOfRange(8, 12).decodeToString() }.getOrNull()
            if (brand in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")) {
                return "image/heic"
            }
        }

        return null
    }

    private fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
