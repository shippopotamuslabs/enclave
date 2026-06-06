package com.browntowndev.enclave.localimagegen

import android.content.Context
import java.io.File

/**
 * QNN HTP libraries must load from [android.content.Context.getApplicationInfo.nativeLibraryDir]
 * (APK jniLibs). Loading stubs from app-private files/ breaks linker namespace resolution
 * for libcdsprpc.so on retail devices.
 */
internal object QnnRuntimeDirResolver {
    private const val QNN_RUNTIME_SUBDIR = "local_image_gen_qnn_runtime"

    data class Resolved(
        val directory: File,
        val usesJniLibs: Boolean,
    )

    fun resolve(context: Context): Resolved {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        if (File(nativeDir, "libQnnHtp.so").isFile && File(nativeDir, "libQnnHtpPrepare.so").isFile) {
            return Resolved(nativeDir, usesJniLibs = true)
        }
        val filesRuntime = File(context.filesDir, QNN_RUNTIME_SUBDIR)
        QnnRuntimeBootstrap.install(context, filesRuntime)
        return Resolved(filesRuntime, usesJniLibs = false)
    }
}
