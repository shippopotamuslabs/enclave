package com.browntowndev.enclave.core.data.download

import android.content.Context
import com.browntowndev.enclave.domain.model.config.requiredArtifacts
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.model.download.ModelPack
import com.browntowndev.enclave.domain.model.download.ModelPackCompleteness
import com.browntowndev.enclave.domain.model.download.ModelPackMemberStatus
import com.browntowndev.enclave.domain.model.download.ModelPackVerificationDepth
import com.browntowndev.enclave.domain.model.download.ParsedModelCatalog
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationPaths
import com.browntowndev.enclave.domain.port.download.HashingPort
import com.browntowndev.enclave.domain.port.download.ModelPackVerifierPort
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
    ) : ModelPackVerifierPort {
        override suspend fun verifyPack(
            pack: ModelPack,
            depth: ModelPackVerificationDepth,
        ): ModelPackCompleteness =
            withContext(Dispatchers.IO) {
                val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
                val memberStatuses =
                    pack.members.map { member ->
                        val verified =
                            if (member.asset.metadata.utilityType == UtilityType.SD15_QNN_PACK) {
                                LocalImageGenerationPaths.hasQnnArtifacts(
                                    File(modelsDir, LocalImageGenerationPaths.SD15_QNN_SUBDIR),
                                )
                            } else {
                                member.asset.metadata.requiredArtifacts().all { artifact ->
                                    verifyArtifact(
                                        modelsDir = modelsDir,
                                        localFileName = artifact.localFileName,
                                        expectedSizeInBytes = artifact.sizeInBytes,
                                        expectedSha256 = artifact.sha256,
                                        depth = depth,
                                    )
                                }
                            }
                        ModelPackMemberStatus(
                            role = member.role,
                            fileName = member.asset.metadata.localFileName,
                            isVerifiedOnDisk = verified,
                        )
                    }
                val installedCount = memberStatuses.count { it.isVerifiedOnDisk }
                val totalCount = memberStatuses.size
                ModelPackCompleteness(
                    packId = pack.packId,
                    installedCount = installedCount,
                    totalCount = totalCount,
                    isComplete = installedCount == totalCount && totalCount > 0,
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
