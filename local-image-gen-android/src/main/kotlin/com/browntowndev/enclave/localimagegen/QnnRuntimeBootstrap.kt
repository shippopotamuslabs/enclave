package com.browntowndev.enclave.localimagegen

import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.device.SnapdragonSocClassifier
import com.browntowndev.enclave.domain.model.media.QnnRuntimeLibraryCatalog
import java.io.File

/**
 * Copies bundled QNN runtime libraries from APK assets into app-private storage for dlopen.
 */
internal object QnnRuntimeBootstrap {
    private const val ASSET_DIR = "qnnlibs"
    private const val TAG = "QnnRuntimeBootstrap"

    fun install(
        context: Context,
        runtimeDir: File,
    ) {
        val tier = SnapdragonSocClassifier.tier(deviceHaystack())
        val required = QnnRuntimeLibraryCatalog.runtimeLibrariesFor(tier)
        runtimeDir.mkdirs()
        required.forEach { name ->
            copyAssetIfNeeded(context, runtimeDir, name)
        }
    }

    private fun deviceHaystack(): String =
        listOfNotNull(
            android.os.Build.HARDWARE,
            android.os.Build.BOARD,
            android.os.Build.SOC_MODEL,
            android.os.Build.SOC_MANUFACTURER,
            System.getProperty("ro.board.platform"),
            System.getProperty("ro.soc.model"),
        ).joinToString(" ")

    private fun copyAssetIfNeeded(
        context: Context,
        runtimeDir: File,
        libraryName: String,
    ) {
        val dest = File(runtimeDir, libraryName)
        if (dest.exists() && dest.length() > 0L) {
            return
        }
        runCatching {
            context.assets.open("$ASSET_DIR/$libraryName").use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            dest.setReadable(true, false)
            dest.setExecutable(true, false)
        }.onFailure { error ->
            Log.w(TAG, "QNN runtime asset missing or unreadable: $libraryName (${error.message})")
        }
    }
}
