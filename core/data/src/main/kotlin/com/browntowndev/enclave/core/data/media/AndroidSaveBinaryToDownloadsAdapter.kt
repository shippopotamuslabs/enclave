package com.browntowndev.enclave.core.data.media

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.browntowndev.enclave.domain.port.media.SaveBinaryToDownloadsPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSaveBinaryToDownloadsAdapter
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SaveBinaryToDownloadsPort {
        override suspend fun save(
            bytes: ByteArray,
            displayName: String,
            mimeType: String,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val values =
                        ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                            put(
                                MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS + "/Enclave",
                            )
                        }
                    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    val resolver = context.contentResolver
                    val uri =
                        resolver.insert(collection, values)
                            ?: error("Failed to insert into MediaStore.Downloads")
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    } ?: error("Failed to open output stream for $uri")
                }
            }
    }
