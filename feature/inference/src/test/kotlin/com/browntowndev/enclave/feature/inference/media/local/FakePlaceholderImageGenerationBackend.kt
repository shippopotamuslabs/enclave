package com.browntowndev.enclave.feature.inference.media.local

import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import java.io.File
import java.io.FileOutputStream

/** Test-only backend that writes a minimal valid PNG for Studio/UI flow tests. */
class FakePlaceholderImageGenerationBackend : LocalImageGenerationBackend {
    override val kind: LocalImageGenerationBackendKind = LocalImageGenerationBackendKind.CPU_MNN

    override suspend fun generate(
        request: LocalImageGenerationNativeRequest,
    ): Result<LocalImageGenerationNativeResult> =
        runCatching {
            val path = "${request.outputPathPrefix}.png"
            writeMinimalPng(File(path), request.width.coerceAtMost(64), request.height.coerceAtMost(64))
            LocalImageGenerationNativeResult(
                outputPaths = listOf(path),
                width = request.width,
                height = request.height,
                elapsedMillis = 1L,
            )
        }

    private fun writeMinimalPng(
        file: File,
        width: Int,
        height: Int,
    ) {
        // 1x1 red PNG (valid IHDR); scales metadata only — sufficient for flow tests.
        val png =
            byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x02, 0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53, 0xDE.toByte(),
                0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, 0x08, 0xD7.toByte(), 0x63, 0xF8.toByte(),
                0xCF.toByte(), 0xC0.toByte(), 0x00, 0x00, 0x03, 0x01, 0x01, 0x00, 0x18, 0xDD.toByte(), 0x8D.toByte(),
                0xB4.toByte(), 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte(),
            )
        FileOutputStream(file).use { it.write(png) }
    }
}