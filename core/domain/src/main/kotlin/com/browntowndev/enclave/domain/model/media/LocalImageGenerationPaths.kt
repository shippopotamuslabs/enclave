package com.browntowndev.enclave.domain.model.media

import java.io.File

/**
 * Resolves on-disk directories for QNN / MNN packages relative to the catalog download folder
 * (parent of the verified GGUF sentinel file).
 */
object LocalImageGenerationPaths {
    const val SD15_QNN_SUBDIR = "sd15-qnn"
    const val SD15_CPU_SUBDIR = "sd15-cpu"
    const val SDXL_QNN_SUBDIR = "sdxl-qnn"

    fun resolveQnnModelDir(downloadedModelParentDir: String, relativeModelDir: String = SD15_QNN_SUBDIR): String {
        val parent = File(downloadedModelParentDir)
        val nested = File(parent, relativeModelDir)
        return qnnArtifactsDir(nested)?.absolutePath
            ?: qnnArtifactsDir(parent)?.absolutePath
            ?: nested.absolutePath
    }

    fun resolveMnnModelDir(downloadedModelParentDir: String): String =
        resolveSubdirOrParent(downloadedModelParentDir, SD15_CPU_SUBDIR, ::hasMnnArtifacts)

    fun hasQnnArtifacts(dir: File): Boolean =
        qnnArtifactsDir(dir) != null

    fun hasMnnArtifacts(dir: File): Boolean =
        REQUIRED_MNN_FILES.all { File(dir, it).isFile }

    private fun resolveSubdirOrParent(
        parentPath: String,
        subdirName: String,
        predicate: (File) -> Boolean,
    ): String {
        val parent = File(parentPath)
        val nested = File(parent, subdirName)
        return when {
            predicate(nested) -> nested.absolutePath
            predicate(parent) -> parent.absolutePath
            else -> nested.absolutePath
        }
    }

    private fun qnnArtifactsDir(dir: File): File? {
        if (hasRequiredFiles(dir, REQUIRED_QNN_FILES)) {
            return dir
        }
        return dir
            .listFiles()
            ?.filter { it.isDirectory && it.name != "__MACOSX" }
            ?.sortedBy { it.name }
            ?.firstOrNull { hasRequiredFiles(it, REQUIRED_QNN_FILES) }
    }

    private fun hasRequiredFiles(
        dir: File,
        fileNames: List<String>,
    ): Boolean = fileNames.all { File(dir, it).isFile }

    private val REQUIRED_QNN_FILES =
        listOf(
            "clip_v2.mnn",
            "token_emb.bin",
            "pos_emb.bin",
            "unet.bin",
            "vae_decoder.bin",
            "tokenizer.json",
        )

    private val REQUIRED_MNN_FILES =
        listOf(
            "clip.mnn",
            "unet.mnn",
            "vae_decoder.mnn",
            "tokenizer.json",
        )
}
