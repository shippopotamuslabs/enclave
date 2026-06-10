package com.browntowndev.enclave.localimagegen

import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.device.SnapdragonSocClassifier
import com.browntowndev.enclave.domain.model.media.QnnRuntimeLibraryCatalog
import java.io.File

/**
 * Copies bundled QNN runtime libraries from APK assets into app-private storage for dlopen.
 *
 * Hexagon loads skeleton libraries from [DSP_LIBRARY_PATH], which must point at this directory.
 * Local Dream copies every file under assets/qnnlibs/; we mirror that so tier mis-detection
 * cannot skip libraries such as libQnnHtpV81Skel.so required for SDXL.
 */
internal object QnnRuntimeBootstrap {
    private const val ASSET_DIR = "qnnlibs"
    private const val TAG = "QnnRuntimeBootstrap"

    fun install(
        context: Context,
        runtimeDir: File,
    ) {
        val bundled = listBundledLibraries(context)
        if (bundled.isNotEmpty()) {
            installLibraries(context, runtimeDir, bundled)
            return
        }
        val tier = SnapdragonSocClassifier.tier(deviceHaystack())
        installLibraries(context, runtimeDir, QnnRuntimeLibraryCatalog.runtimeLibrariesFor(tier))
    }

    fun installLibraries(
        context: Context,
        runtimeDir: File,
        libraryNames: List<String>,
    ) {
        runtimeDir.mkdirs()
        // Hexagon DSP loads QNN shared objects via dlopen from this tree. Android's private
        // app-cache default mode blocks other UIDs (including the DSP loader) from traversing
        // the directory, so mark it world-readable/executable for the duration of inference.
        runtimeDir.setReadable(true, /* ownerOnly */ false)
        runtimeDir.setExecutable(true, /* ownerOnly */ false)
        libraryNames.forEach { name ->
            copyAssetIfNeeded(context, runtimeDir, name)
        }
    }

    private fun listBundledLibraries(context: Context): List<String> =
        runCatching {
            context.assets
                .list(ASSET_DIR)
                ?.filter { it.endsWith(".so") }
                ?.sorted()
                ?: emptyList()
        }.getOrElse { error ->
            Log.w(TAG, "Failed to list $ASSET_DIR assets: ${error.message}")
            emptyList()
        }

    private fun deviceHaystack(): String =
        listOfNotNull(
            android.os.Build.HARDWARE,
            android.os.Build.BOARD,
            android.os.Build.SOC_MODEL,
            android.os.Build.SOC_MANUFACTURER,
        ).joinToString(" ")

    private fun runtimeLibraryMatchesAsset(
        dest: File,
        assetBytes: ByteArray,
    ): Boolean =
        dest.exists() &&
            dest.length() == assetBytes.size.toLong() &&
            assetBytes.isNotEmpty() &&
            dest.readBytes().contentEquals(assetBytes)

    private fun copyAssetIfNeeded(
        context: Context,
        runtimeDir: File,
        libraryName: String,
    ) {
        val dest = File(runtimeDir, libraryName)
        runCatching {
            val assetPath = "$ASSET_DIR/$libraryName"
            val assetBytes =
                context.assets.open(assetPath).use { input ->
                    input.readBytes()
                }
            if (runtimeLibraryMatchesAsset(dest, assetBytes)) {
                return
            }
            dest.writeBytes(assetBytes)
            dest.setReadable(true, /* ownerOnly */ false)
            dest.setExecutable(true, /* ownerOnly */ false)
            Log.d(TAG, "Installed QNN runtime library: $libraryName (${dest.length()} bytes)")
        }.onFailure { error ->
            Log.e(TAG, "QNN runtime asset missing or unreadable: $libraryName (${error.message})")
        }
    }
}
