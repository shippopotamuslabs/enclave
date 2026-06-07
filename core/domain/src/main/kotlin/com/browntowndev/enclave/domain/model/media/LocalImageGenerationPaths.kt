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
    const val JUGGERNAUT_XL_QNN_SUBDIR = "juggernaut-qnn"

    fun resolveQnnModelDir(downloadedModelParentDir: String, relativeModelDir: String = SD15_QNN_SUBDIR): String =
        resolveQnnLaunchDir(
            resolvedStoragePath = downloadedModelParentDir,
            family = LocalImageGenerationModelFamily.SD15,
            relativePackSubdir = relativeModelDir,
        )

    fun resolveSdxlQnnModelDir(downloadedModelParentDir: String): String =
        resolveQnnLaunchDir(
            resolvedStoragePath = downloadedModelParentDir,
            family = LocalImageGenerationModelFamily.SDXL,
            relativePackSubdir = SDXL_QNN_SUBDIR,
        )

    /**
     * Resolves the directory passed to the native QNN backend (artifact folder, not pack root).
     *
     * [resolvedStoragePath] may be a catalog sentinel file, a pack root (`sdxl-qnn`), or an
     * extracted artifact directory.
     */
    fun resolveQnnLaunchDir(
        resolvedStoragePath: String,
        family: LocalImageGenerationModelFamily,
        relativePackSubdir: String,
    ): String {
        val requiredFiles = qnnRequiredFiles(family)
        val resolved = File(resolvedStoragePath)
        if (resolved.isDirectory) {
            qnnArtifactsDir(resolved, requiredFiles)?.let { return it.absolutePath }
        }
        val modelsDir =
            when {
                resolved.isDirectory && resolved.name == relativePackSubdir ->
                    resolved.parentFile?.absolutePath ?: resolved.absolutePath
                resolved.isDirectory -> resolved.absolutePath
                else -> resolved.parentFile?.absolutePath ?: resolved.absolutePath
            }
        val packRoot = File(modelsDir, relativePackSubdir)
        return qnnArtifactsDir(packRoot, requiredFiles)?.absolutePath ?: packRoot.absolutePath
    }

    private fun qnnRequiredFiles(family: LocalImageGenerationModelFamily): List<String> =
        when (family) {
            LocalImageGenerationModelFamily.SDXL -> REQUIRED_SDXL_QNN_FILES
            LocalImageGenerationModelFamily.SD15 -> REQUIRED_SD15_QNN_FILES
        }

    fun resolveMnnModelDir(downloadedModelParentDir: String): String =
        resolveSubdirOrParent(downloadedModelParentDir, SD15_CPU_SUBDIR, ::hasMnnArtifacts)

    /** SD 1.5 QNN packs ship `clip_v2.mnn` and single text-encoder bins. */
    fun hasSd15QnnArtifacts(dir: File): Boolean =
        qnnArtifactsDir(dir, REQUIRED_SD15_QNN_FILES) != null

    /**
     * SDXL QNN packs ship dual CLIP weights (`clip.mnn`, `clip_2.mnn`) plus paired embedding bins.
     * The native backend uses `clip.mnn` as the launch sentinel for this layout.
     */
    fun hasSdxlQnnArtifacts(dir: File): Boolean =
        qnnArtifactsDir(dir, REQUIRED_SDXL_QNN_FILES) != null

    fun hasJuggernautXlQnnArtifacts(dir: File): Boolean =
        qnnArtifactsDir(dir, REQUIRED_SDXL_QNN_FILES) != null

    @Deprecated(
        message = "Use hasSd15QnnArtifacts or hasSdxlQnnArtifacts for pack-specific checks.",
        replaceWith = ReplaceWith("hasSd15QnnArtifacts(dir)"),
    )
    fun hasQnnArtifacts(dir: File): Boolean = hasSd15QnnArtifacts(dir)

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

    private fun qnnArtifactsDir(
        dir: File,
        requiredFiles: List<String>,
    ): File? {
        if (hasRequiredFiles(dir, requiredFiles)) {
            return dir
        }
        return dir
            .listFiles()
            ?.filter { it.isDirectory && it.name != "__MACOSX" }
            ?.sortedBy { it.name }
            ?.firstOrNull { hasRequiredFiles(it, requiredFiles) }
    }

    private fun hasRequiredFiles(
        dir: File,
        fileNames: List<String>,
    ): Boolean = fileNames.all { File(dir, it).isFile }

    val OPTIONAL_IMG2IMG_QNN_FILES: List<String> = listOf("vae_encoder.bin")

    val OPTIONAL_IMG2IMG_MNN_FILES: List<String> = listOf("vae_encoder.mnn")

    fun hasImg2ImgCapability(dir: File): Boolean =
        OPTIONAL_IMG2IMG_QNN_FILES.all { File(dir, it).isFile } ||
            OPTIONAL_IMG2IMG_MNN_FILES.all { File(dir, it).isFile }

    val REQUIRED_SD15_QNN_FILES: List<String> =
        listOf(
            "clip_v2.mnn",
            "token_emb.bin",
            "pos_emb.bin",
            "unet.bin",
            "vae_decoder.bin",
            "tokenizer.json",
        )

    val REQUIRED_SDXL_QNN_FILES: List<String> =
        listOf(
            "clip.mnn",
            "clip_2.mnn",
            "token_emb.bin",
            "token_emb_2.bin",
            "pos_emb.bin",
            "pos_emb_2.bin",
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
