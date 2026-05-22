package com.browntowndev.enclave.core.data.repository.message

import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisDao
import com.browntowndev.enclave.core.data.local.MessageVisionAnalysisEntity
import com.browntowndev.enclave.core.data.mapper.toDomain
import com.browntowndev.enclave.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepair
import com.browntowndev.enclave.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.enclave.domain.model.inference.ModelType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageVisionWriter
    @Inject
    constructor(
        private val syncTrigger: CloudSyncTrigger,
        private val messageVisionAnalysisDao: MessageVisionAnalysisDao,
        private val healer: SensitivePersistenceSelfHealer,
        private val readRepairApplier: SensitiveRoomReadRepairApplier,
    ) {
        suspend fun saveVisionAnalysis(
            userMessageId: MessageId,
            imageUri: String,
            promptText: String,
            analysisText: String,
            modelType: ModelType,
        ) {
            val now = System.currentTimeMillis()
            messageVisionAnalysisDao.upsert(
                SensitiveEntityFieldEncryptors.encrypt(
                    MessageVisionAnalysisEntity(
                        id = UUID.randomUUID().toString(),
                        userMessageId = userMessageId,
                        imageUri = imageUri,
                        promptText = promptText,
                        analysisText = analysisText,
                        modelType = modelType,
                        createdAt = now,
                        updatedAt = now,
                        pendingSync = 1,
                    ),
                    healer,
                ),
            )
            syncTrigger.trigger()
        }

        suspend fun getVisionAnalysesForMessages(userMessageIds: List<MessageId>): Map<MessageId, List<MessageVisionAnalysis>> {
            if (userMessageIds.isEmpty()) return emptyMap()
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out =
                messageVisionAnalysisDao
                    .getByUserMessageIds(userMessageIds)
                    .map { SensitiveEntityFieldEncryptors.decrypt(it, healer, repairs).toDomain() }
                    .groupBy { it.userMessageId }
            readRepairApplier.apply(repairs)
            return out
        }
    }
