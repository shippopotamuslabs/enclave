package com.browntowndev.enclave.feature.inference.titlegen

import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import java.io.File

/**
 * Seeds [utility_models] registry rows and copies adb-staged title assets (debug harness only).
 *
 * Matches checksums in root [model_config.json] so [UtilityModelFilePort] can resolve paths
 * without waiting for a remote download.
 */
object TitleGenHarnessSeed {
    /** World-readable staging dir populated by [scripts/run_title_gen_harness.sh]. */
    private const val STAGING_DIR = "/data/local/tmp/titlegen"

    suspend fun ensureRegistry(utilityModelRepository: UtilityModelRepositoryPort) {
        harnessCatalog.forEach { metadata ->
            utilityModelRepository.upsert(metadata)
        }
    }

    /**
     * Copies staged assets into the app-owned external models directory.
     *
     * Files pushed directly under Android/data/... are owned by shell and are invisible to the app;
     * staging under [STAGING_DIR] is readable and copied here with the app UID.
     */
    fun ensureAssets(context: Context) {
        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
        if (!modelsDir.exists() && !modelsDir.mkdirs()) {
            Log.w(TitleGenHarnessDiagnostics.LOG_TAG, "Failed to create modelsDir=${modelsDir.absolutePath}")
            return
        }
        harnessCatalog.forEach { metadata ->
            val destination = File(modelsDir, metadata.localFileName)
            if (destination.length() == metadata.sizeInBytes) {
                Log.i(
                    TitleGenHarnessDiagnostics.LOG_TAG,
                    "Asset already present: ${destination.name} (${destination.length()}b)",
                )
                return@forEach
            }
            val staging = File(STAGING_DIR, metadata.remoteFileName)
            if (!staging.isFile || staging.length() != metadata.sizeInBytes) {
                Log.w(
                    TitleGenHarnessDiagnostics.LOG_TAG,
                    "Staging missing or wrong size for ${metadata.remoteFileName}: " +
                        "path=${staging.absolutePath} exists=${staging.exists()} size=${staging.length()}",
                )
                return@forEach
            }
            Log.i(
                TitleGenHarnessDiagnostics.LOG_TAG,
                "Copying ${metadata.localFileName} from staging (${metadata.sizeInBytes}b)...",
            )
            runCatching {
                staging.inputStream().buffered().use { input ->
                    destination.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }
            }.onFailure { error ->
                Log.w(
                    TitleGenHarnessDiagnostics.LOG_TAG,
                    "Copy failed for ${metadata.localFileName}: ${error.message}",
                    error,
                )
                destination.delete()
            }.onSuccess {
                Log.i(
                    TitleGenHarnessDiagnostics.LOG_TAG,
                    "Copied ${metadata.localFileName} -> ${destination.absolutePath} (${destination.length()}b)",
                )
            }
        }
    }

    internal val harnessCatalog =
        listOf(
            UtilityModelMetadata(
                id = UtilityModelId(""),
                utilityType = UtilityType.TITLE_GENERATOR_ENCODER,
                huggingFaceModelName = "",
                remoteFileName = "model_q4.onnx",
                localFileName = "model_q4.onnx",
                sha256 = "d1a705712e93aafaba1346b32245fa59e7857a46e2272003c0e8c524977e0de8",
                sizeInBytes = 183_442L,
                modelFileFormat = ModelFileFormat.ONNX,
                source = DownloadSource.CLOUDFLARE_R2,
            ),
            UtilityModelMetadata(
                id = UtilityModelId(""),
                utilityType = UtilityType.TITLE_GENERATOR_DECODER,
                huggingFaceModelName = "",
                remoteFileName = "model_q4.onnx_data",
                localFileName = "model_q4.onnx_data",
                sha256 = "71ec6ad38a4c463dcb3dba671d06a1d9861be3a23e51290d818b95c0b7d2a5db",
                sizeInBytes = 293_629_952L,
                modelFileFormat = ModelFileFormat.ONNX,
                source = DownloadSource.CLOUDFLARE_R2,
            ),
            UtilityModelMetadata(
                id = UtilityModelId(""),
                utilityType = UtilityType.TITLE_GENERATOR_TOKENIZER,
                huggingFaceModelName = "",
                remoteFileName = "tokenizer.json",
                localFileName = "tokenizer.json",
                sha256 = "29d43b4be8e8a896fefd7cd836ca6d6b4eedd249f823866ce0453b368e646f49",
                sizeInBytes = 3_297_793L,
                modelFileFormat = ModelFileFormat.TXT,
                source = DownloadSource.CLOUDFLARE_R2,
            ),
        )
}
