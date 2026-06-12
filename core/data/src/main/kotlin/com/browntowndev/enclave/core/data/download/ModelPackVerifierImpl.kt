package com.browntowndev.enclave.core.data.download

import android.content.Context
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.config.requiredArtifacts
import com.browntowndev.enclave.domain.model.download.JuggernautXlQnnPackCatalog
import com.browntowndev.enclave.domain.model.download.LocalImagePackIds
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.model.download.ModelPack
import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackMemberStatus
import com.browntowndev.enclave.domain.model.download.ModelPackVerificationDepth
import com.browntowndev.enclave.domain.model.download.ParsedModelCatalog
import com.browntowndev.enclave.domain.model.download.Sd15QnnPackCatalog
import com.browntowndev.enclave.domain.model.download.SdxlQnnPackCatalog
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.media.LocalImageGenTriState
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelFamily
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationPaths
import com.browntowndev.enclave.domain.port.download.HashingPort
import com.browntowndev.enclave.domain.port.download.ModelPackVerifierPort
import com.browntowndev.enclave.domain.port.media.LocalImageGenerationCapabilityPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelPackVerifierImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val hashingPort: HashingPort,
        private val capabilityPort: LocalImageGenerationCapabilityPort,
    ) : ModelPackVerifierPort {
        override suspend fun verifyPack(
            pack: ModelPack,
            depth: ModelPackVerificationDepth,
        ): ModelPackCompleteness =
            withContext(Dispatchers.IO) {
                val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
                val capabilities = capabilityPort.getCapabilities()
                // The SD15 weights member is variant-substituted per device: CPU-routed tiers
                // download the MNN zip, which installs under sd15-cpu instead of sd15-qnn.
                val isSd15CpuFallback =
                    pack.packId == LocalImagePackIds.SD15 &&
                        capabilities.sd15NpuSupported == LocalImageGenTriState.NO &&
                        capabilities.sd15CpuSupported == LocalImageGenTriState.YES

                val memberStatuses =
                    pack.members.map { member ->
                        val verified =
                            when (member.asset.metadata.utilityType) {
                                UtilityType.SD15_QNN_PACK ->
                                    if (isSd15CpuFallback) {
                                        LocalImageGenerationPaths.hasMnnArtifacts(
                                            File(modelsDir, LocalImageGenerationPaths.SD15_CPU_SUBDIR),
                                        )
                                    } else {
                                        LocalImageGenerationPaths.hasSd15QnnArtifacts(
                                            File(modelsDir, LocalImageGenerationPaths.SD15_QNN_SUBDIR),
                                        )
                                    }
                                UtilityType.SDXL_QNN_PACK ->
                                    LocalImageGenerationPaths.hasSdxlQnnArtifacts(
                                        File(modelsDir, LocalImageGenerationPaths.SDXL_QNN_SUBDIR),
                                    )
                                UtilityType.JUGGERNAUT_XL_QNN_PACK ->
                                    LocalImageGenerationPaths.hasJuggernautXlQnnArtifacts(
                                        File(modelsDir, LocalImageGenerationPaths.JUGGERNAUT_XL_QNN_SUBDIR),
                                    )
                                else -> verifyNonUtilityMember(modelsDir, member, depth)
                            }
                        ModelPackMemberStatus(
                            role = member.role,
                            fileName = member.asset.metadata.localFileName,
                            isVerifiedOnDisk = verified,
                        )
                    }
                val installedCount = memberStatuses.count { it.isVerifiedOnDisk }
                val totalCount = memberStatuses.size
                val baseComplete = installedCount == totalCount && totalCount > 0
                val requiresImg2ImgEncoder =
                    pack.slot == ModelType.IMAGE_EDIT && pack.packId == LocalImagePackIds.JUGGERNAUT
                val img2ImgReady =
                    if (requiresImg2ImgEncoder) {
                        val juggernautDir = File(modelsDir, LocalImageGenerationPaths.JUGGERNAUT_XL_QNN_SUBDIR)
                        // Resolve the artifact dir the same way the generator does: the QNN pack may
                        // extract into a nested subdir, so vae_encoder.bin sits beside the other
                        // artifacts there, not at the pack root.
                        val launchDir =
                            LocalImageGenerationPaths.resolveQnnLaunchDir(
                                resolvedStoragePath = juggernautDir.absolutePath,
                                family = LocalImageGenerationModelFamily.SDXL,
                                relativePackSubdir = LocalImageGenerationPaths.JUGGERNAUT_XL_QNN_SUBDIR,
                            )
                        LocalImageGenerationPaths.hasImg2ImgCapability(File(launchDir))
                    } else {
                        true
                    }
                ModelPackCompleteness(
                    packId = pack.packId,
                    installedCount = installedCount,
                    totalCount = totalCount,
                    isComplete = baseComplete && img2ImgReady,
                    members = memberStatuses,
                )
            }

        override suspend fun verifyPackById(
            catalog: ParsedModelCatalog,
            packId: String,
            depth: ModelPackVerificationDepth,
        ): ModelPackCompleteness? {
            val pack = catalog.packById(packId) ?: return null
            return verifyPack(pack, depth)
        }

        private fun verifyNonUtilityMember(
            modelsDir: File,
            member: com.browntowndev.enclave.domain.model.download.ModelPackMember,
            depth: ModelPackVerificationDepth,
        ): Boolean {
            val sha256 = member.asset.metadata.sha256
            val qnnPackDir =
                when {
                    JuggernautXlQnnPackCatalog.findBySha256(sha256) != null ->
                        File(modelsDir, LocalImageGenerationPaths.JUGGERNAUT_XL_QNN_SUBDIR)
                    SdxlQnnPackCatalog.findBySha256(sha256) != null ->
                        File(modelsDir, LocalImageGenerationPaths.SDXL_QNN_SUBDIR)
                    Sd15QnnPackCatalog.findBySha256(sha256) != null ->
                        File(modelsDir, LocalImageGenerationPaths.SD15_QNN_SUBDIR)
                    else -> null
                }
            if (qnnPackDir != null) {
                return when {
                    JuggernautXlQnnPackCatalog.findBySha256(sha256) != null ->
                        LocalImageGenerationPaths.hasJuggernautXlQnnArtifacts(qnnPackDir)
                    SdxlQnnPackCatalog.findBySha256(sha256) != null ->
                        LocalImageGenerationPaths.hasSdxlQnnArtifacts(qnnPackDir)
                    else -> LocalImageGenerationPaths.hasSd15QnnArtifacts(qnnPackDir)
                }
            }
            return member.asset.metadata.requiredArtifacts().all { artifact ->
                verifyArtifact(
                    modelsDir = modelsDir,
                    localFileName = artifact.localFileName,
                    expectedSizeInBytes = artifact.sizeInBytes,
                    expectedSha256 = artifact.sha256,
                    depth = depth,
                )
            }
        }

        private fun verifyArtifact(
            modelsDir: File,
            localFileName: String,
            expectedSizeInBytes: Long,
            expectedSha256: String,
            depth: ModelPackVerificationDepth,
        ): Boolean {
            val file = safeFile(modelsDir, localFileName)
            return try {
                if (!file.exists() || file.length() != expectedSizeInBytes) {
                    return false
                }
                when (depth) {
                    ModelPackVerificationDepth.SIZE_ON_DISK -> true
                    ModelPackVerificationDepth.SHA256_INTEGRITY ->
                        hashingPort.calculateSha256(file.absolutePath).equals(expectedSha256, ignoreCase = true)
                }
            } catch (_: Exception) {
                false
            }
        }

        private fun safeFile(
            parentDir: File,
            filename: String,
        ): File {
            require(filename.isNotBlank()) { "Filename must not be blank" }
            val safeName = File(filename).name
            val resolvedFile = File(parentDir, safeName)
            val parentCanonical = parentDir.canonicalPath + File.separator
            require(resolvedFile.canonicalPath.startsWith(parentCanonical)) {
                "Resolved path escapes target directory: $filename"
            }
            return resolvedFile
        }
    }
