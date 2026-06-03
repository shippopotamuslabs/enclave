package com.browntowndev.enclave.core.data.media

import android.content.ContentResolver
import android.net.Uri
import java.io.File

internal fun resolveLocalMediaFile(localUri: String): File {
    if (localUri.startsWith("file://")) {
        return File(localUri.removePrefix("file://"))
    }
    if (!localUri.contains("://")) {
        return File(localUri)
    }

    val parsedUri = Uri.parse(localUri)
    return when (parsedUri.scheme) {
        ContentResolver.SCHEME_FILE ->
            File(
                requireNotNull(parsedUri.path) {
                    "File URI must contain a path: $localUri"
                },
            )
        else -> File(parsedUri.path ?: localUri)
    }
}
