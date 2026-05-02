package com.browntowndev.pocketcrew.core.data.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentMetadata
import com.browntowndev.pocketcrew.domain.port.media.FileAttachmentPort
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
class AndroidFileAttachmentPort @Inject constructor(
    @ApplicationContext private val context: Context
) : FileAttachmentPort {

    override suspend fun readFileContent(uri: String): FileAttachmentMetadata = withContext(Dispatchers.IO) {
        val androidUri = Uri.parse(uri)
        val mimeType = context.contentResolver.getType(androidUri)
        
        // Check for supported MIME types
        if (mimeType != null && !isSupported(mimeType)) {
            throw NotImplementedError("File type $mimeType is not yet supported for analysis.")
        }

        val name = getFileName(androidUri) ?: "attached_file"
        
        val content = context.contentResolver.openInputStream(androidUri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readText()
            }
        } ?: throw IllegalStateException("Unable to open file stream for $uri")

        FileAttachmentMetadata(
            name = name,
            mimeType = mimeType,
            content = content
        )
    }

    private fun isSupported(mimeType: String): Boolean {
        return mimeType == "text/plain" || 
               mimeType == "text/csv" || 
               mimeType == "text/markdown" ||
               mimeType == "application/json" ||
               mimeType.startsWith("text/")
    }

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
}
