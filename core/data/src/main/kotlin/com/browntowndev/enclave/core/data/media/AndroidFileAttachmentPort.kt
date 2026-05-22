package com.browntowndev.enclave.core.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.browntowndev.enclave.domain.port.media.FileAttachmentMetadata
import com.browntowndev.enclave.domain.port.media.FileAttachmentPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of [FileAttachmentPort] using [ContentResolver].
 */
@Singleton
class AndroidFileAttachmentPort
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : FileAttachmentPort {
        override suspend fun readFileContent(uri: String): FileAttachmentMetadata =
            withContext(Dispatchers.IO) {
                val androidUri = Uri.parse(uri)
                val mimeType = context.contentResolver.getType(androidUri) ?: "application/octet-stream"
                val name = getFileName(androidUri) ?: "attached_file"

                val content =
                    if (isTextBased(mimeType)) {
                        context.contentResolver.openInputStream(androidUri)?.use { inputStream ->
                            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                reader.readText()
                            }
                        } ?: throw IllegalStateException("Unable to open file stream for $uri")
                    } else {
                        val bytes =
                            context.contentResolver.openInputStream(androidUri)?.use { it.readBytes() }
                                ?: throw IllegalStateException("Unable to open file stream for $uri")
                        // Encode binary data as base64 prefix so we know how to decode it later
                        "BASE64:" + Base64.encodeToString(bytes, Base64.NO_WRAP)
                    }

                FileAttachmentMetadata(
                    name = name,
                    mimeType = mimeType,
                    content = content,
                )
            }

        private fun isTextBased(mimeType: String): Boolean =
            mimeType.startsWith("text/") ||
                mimeType == "application/json" ||
                mimeType == "application/xml" ||
                mimeType == "application/javascript" ||
                mimeType == "application/x-yaml" ||
                mimeType == "application/toml"

        private fun getFileName(uri: Uri): String? {
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) return cursor.getString(index)
                    }
                }
            }
            return uri.path?.let { path ->
                val index = path.lastIndexOf('/')
                if (index != -1) path.substring(index + 1) else path
            }
        }

        override suspend fun readBytes(uri: String): ByteArray =
            withContext(Dispatchers.IO) {
                val androidUri = Uri.parse(uri)
                context.contentResolver.openInputStream(androidUri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Unable to open file stream for $uri")
            }
    }
