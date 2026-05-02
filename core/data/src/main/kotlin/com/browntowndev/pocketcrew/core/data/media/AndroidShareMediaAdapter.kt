package com.browntowndev.pocketcrew.core.data.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.browntowndev.pocketcrew.domain.port.media.ShareMediaPort
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidShareMediaAdapter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ShareMediaPort {

    override fun shareMedia(uris: List<String>, mimeType: String): Unit {
        if (uris.isEmpty()) {
            return
        }

        val sharedUris = uris.map(::toShareUri)
        val shareIntent = when (sharedUris.size) {
            1 -> Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, sharedUris.single())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(sharedUris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val chooserIntent = Intent.createChooser(shareIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooserIntent)
    }

    private fun toShareUri(rawUri: String): Uri {
        val parsedUri = Uri.parse(rawUri)
        return when (parsedUri.scheme) {
            ContentResolver.SCHEME_CONTENT -> parsedUri
            ContentResolver.SCHEME_FILE -> fileProviderUri(
                File(
                    requireNotNull(parsedUri.path) {
                        "File URI must contain a path: $rawUri"
                    },
                ),
            )
            null -> fileProviderUri(File(rawUri))
            else -> parsedUri
        }
    }

    private fun fileProviderUri(filePath: File): Uri {
        return FileProvider.getUriForFile(context, fileProviderAuthority, filePath)
    }

    private val fileProviderAuthority: String
        get() = "${context.packageName}.fileprovider"
}
