package com.browntowndev.pocketcrew.core.data.download.remote

import com.browntowndev.pocketcrew.domain.model.download.ModelConfig
import java.io.File
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object DownloadSecurity {

    private const val R2_HOST = "config.pocketcrew.app"
    private const val HUGGING_FACE_HOST = "huggingface.co"
    private const val HF_SHORT_HOST = "hf.co"
    private val repoSegmentPattern = Regex("^[A-Za-z0-9][A-Za-z0-9._-]*$")

    data class DownloadPaths(
        val targetFile: File,
        val tempFile: File,
        val metaFile: File
    )

    fun requireSafeFileName(fileName: String, fieldName: String = "filename"): String {
        if (fileName.isBlank()) {
            throw SecurityException("$fieldName must not be blank")
        }
        if (fileName == "." || fileName == "..") {
            throw SecurityException("$fieldName must not be a dot path")
        }
        if ('/' in fileName || '\\' in fileName) {
            throw SecurityException("$fieldName must not contain path separators")
        }
        if (fileName.any { it.isISOControl() }) {
            throw SecurityException("$fieldName must not contain control characters")
        }
        return fileName
    }

    fun requireHuggingFaceRepoId(repoId: String): Pair<String, String> {
        require(repoId.isNotBlank()) { "Hugging Face model name is required" }
        val segments = repoId.split('/')
        require(segments.size == 2) { "Hugging Face model name must be in owner/repo format" }

        val owner = segments[0]
        val repo = segments[1]
        require(owner.matches(repoSegmentPattern)) { "Invalid Hugging Face owner segment" }
        require(repo.matches(repoSegmentPattern)) { "Invalid Hugging Face repo segment" }
        return owner to repo
    }

    fun requireTrustedDownloadUrl(url: String): HttpUrl {
        val parsedUrl = url.toHttpUrlOrNull() ?: throw SecurityException("Invalid download URL: $url")
        if (parsedUrl.scheme != "https") {
            throw SecurityException("Only HTTPS downloads are allowed: $url")
        }
        return parsedUrl
    }

    fun requireTrustedRedirect(originalUrl: HttpUrl, finalUrl: HttpUrl) {
        if (finalUrl.scheme != "https") {
            throw SecurityException("Redirected download must remain HTTPS: $finalUrl")
        }

        val redirectAllowed = when {
            originalUrl.host == R2_HOST -> finalUrl.host == R2_HOST
            isHuggingFaceHost(originalUrl.host) -> isHuggingFaceHost(finalUrl.host)
            else -> sameOrigin(originalUrl, finalUrl)
        }

        if (!redirectAllowed) {
            throw SecurityException(
                "Redirected download host is not allowed. Requested=${originalUrl.host}, actual=${finalUrl.host}"
            )
        }
    }

    fun resolveDownloadPaths(targetDir: File, fileName: String): DownloadPaths {
        require(targetDir.exists()) { "Target directory does not exist: ${targetDir.path}" }
        require(targetDir.isDirectory) { "Target directory is not a directory: ${targetDir.path}" }

        val safeFileName = requireSafeFileName(fileName)
        val canonicalTargetDir = targetDir.canonicalFile

        fun isSafeChildOf(child: File, parent: File): Boolean {
            val childCanonical = child.canonicalPath
            val parentCanonical = parent.canonicalPath + File.separator
            return childCanonical.startsWith(parentCanonical)
        }

        fun resolveChild(name: String): File {
            val resolved = File(canonicalTargetDir, File(name).name).canonicalFile
            if (!isSafeChildOf(resolved, canonicalTargetDir)) {
                throw SecurityException("Resolved path escapes target directory: $name")
            }
            return resolved
        }
        return DownloadPaths(
            targetFile = resolveChild(safeFileName),
            tempFile = resolveChild("$safeFileName${ModelConfig.TEMP_EXTENSION}"),
            metaFile = resolveChild("$safeFileName${ModelConfig.TEMP_META_EXTENSION}")
        )
    }

    private fun isHuggingFaceHost(host: String): Boolean {
        val normalizedHost = host.lowercase()
        return normalizedHost == HUGGING_FACE_HOST ||
            normalizedHost.endsWith(".$HUGGING_FACE_HOST") ||
            normalizedHost == HF_SHORT_HOST ||
            normalizedHost.endsWith(".$HF_SHORT_HOST")
    }

    private fun sameOrigin(originalUrl: HttpUrl, finalUrl: HttpUrl): Boolean {
        return originalUrl.scheme == finalUrl.scheme &&
            originalUrl.host == finalUrl.host &&
            originalUrl.port == finalUrl.port
    }
}
