package com.browntowndev.pocketcrew.core.data.media

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.browntowndev.pocketcrew.domain.model.media.ShareDocumentBytesInput
import com.browntowndev.pocketcrew.domain.port.media.ShareMediaPort
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
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

    override fun shareDocumentBytes(items: List<ShareDocumentBytesInput>) {
        if (items.isEmpty()) {
            return
        }
        val resolver = context.contentResolver
        val uris = ArrayList<Uri>(items.size)
        for (item in items) {
            val f = File(context.cacheDir, "share_${UUID.randomUUID()}_${item.suggestedFileName.replace('/', '_')}")
            f.writeBytes(item.bytes)
            uris.add(FileProvider.getUriForFile(context, fileProviderAuthority, f))
        }
        if (uris.size == 1) {
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = items[0].mimeType
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(
                Intent.createChooser(intent, null).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return
        }
        val intent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    putExtra(Intent.EXTRA_MIME_TYPES, items.map { it.mimeType }.toTypedArray())
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val clip =
                    ClipData.newUri(resolver, items[0].suggestedFileName, uris[0])
                for (i in 1 until items.size) {
                    clip.addItem(ClipData.Item(uris[i]))
                }
                clipData = clip
            }
        context.startActivity(
            Intent.createChooser(intent, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
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
