package com.browntowndev.enclave.core.data.security

import com.browntowndev.enclave.domain.port.inference.LoggingPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensitivePersistenceSelfHealer
    @Inject
    constructor(
        private val localEncryption: LocalDataEncryptionService,
        private val loggingPort: LoggingPort,
    ) {
        fun repairString(
            ref: SensitiveFieldRef,
            entityId: String,
            value: String?,
            sourceSide: String = ref.boundary.name,
        ): SensitiveRepairResult {
            if (value == null) {
                return SensitiveRepairResult(value = null, action = SensitiveRepairAction.NONE)
            }
            if (localEncryption.isEncrypted(value)) {
                return SensitiveRepairResult(value = value, action = SensitiveRepairAction.NONE)
            }

            return if (localEncryption.isUnlocked()) {
                val aad = AadContext(ref.owner, ref.field, entityId).toBytes()
                val encrypted = localEncryption.encryptString(value, aad)
                val event = SensitiveRepairEvent(ref, SensitiveRepairAction.REPAIRED, entityId, sourceSide)
                logRepair(event)
                SensitiveRepairResult(value = encrypted, action = SensitiveRepairAction.REPAIRED, event = event)
            } else {
                val event = SensitiveRepairEvent(ref, SensitiveRepairAction.DEFERRED_LOCKED, entityId, sourceSide)
                logRepair(event)
                SensitiveRepairResult(value = value, action = SensitiveRepairAction.DEFERRED_LOCKED, event = event)
            }
        }

        fun requireEncrypted(
            ref: SensitiveFieldRef,
            entityId: String,
            value: String?,
            sourceSide: String = ref.boundary.name,
        ): String? {
            val repaired = repairString(ref = ref, entityId = entityId, value = value, sourceSide = sourceSide)
            if (repaired.action == SensitiveRepairAction.DEFERRED_LOCKED) {
                throw SensitivePersistenceLockedException("Vault locked")
            }
            return repaired.value
        }

        fun decryptForRead(
            ref: SensitiveFieldRef,
            entityId: String,
            value: String?,
            sourceSide: String = ref.boundary.name,
            readRepairs: MutableList<SensitiveRoomReadRepair>? = null,
        ): String? {
            if (value == null) return null
            if (!localEncryption.isEncrypted(value)) {
                val repaired = repairString(ref = ref, entityId = entityId, value = value, sourceSide = sourceSide)
                val repairedForRoom =
                    repaired.action == SensitiveRepairAction.REPAIRED &&
                        repaired.value != null &&
                        ref.boundary == PersistenceBoundary.ROOM
                if (repairedForRoom) {
                    readRepairs?.add(SensitiveRoomReadRepair(ref = ref, entityId = entityId, encryptedValue = repaired.value))
                    return try {
                        val aad = AadContext(ref.owner, ref.field, entityId).toBytes()
                        localEncryption.decryptString(repaired.value, aad)
                    } catch (e: IllegalStateException) {
                        throw SensitivePersistenceLockedException("Vault locked").also { it.initCause(e) }
                    } catch (e: MalformedEnvelopeException) {
                        val event = SensitiveRepairEvent(ref, SensitiveRepairAction.REJECTED_MALFORMED_ENVELOPE, entityId, sourceSide)
                        logRepair(event)
                        loggingPort.recordException(TAG, "Malformed envelope for ${ref.owner}.${ref.field}", e)
                        null
                    } catch (e: Exception) {
                        val event = SensitiveRepairEvent(ref, SensitiveRepairAction.REJECTED_INVALID_CIPHERTEXT, entityId, sourceSide)
                        logRepair(event)
                        loggingPort.recordException(TAG, "Sensitive persistence decrypt failed for ${ref.owner}.${ref.field}", e)
                        null
                    }
                }
                return value
            }

            return try {
                val aad = AadContext(ref.owner, ref.field, entityId).toBytes()
                localEncryption.decryptString(value, aad)
            } catch (e: IllegalStateException) {
                throw SensitivePersistenceLockedException("Vault locked").also { it.initCause(e) }
            } catch (e: MalformedEnvelopeException) {
                val event = SensitiveRepairEvent(ref, SensitiveRepairAction.REJECTED_MALFORMED_ENVELOPE, entityId, sourceSide)
                logRepair(event)
                loggingPort.recordException(TAG, "Malformed envelope for ${ref.owner}.${ref.field}", e)
                null
            } catch (e: Exception) {
                val event = SensitiveRepairEvent(ref, SensitiveRepairAction.REJECTED_INVALID_CIPHERTEXT, entityId, sourceSide)
                logRepair(event)
                loggingPort.recordException(TAG, "Sensitive persistence decrypt failed for ${ref.owner}.${ref.field}", e)
                null
            }
        }

        fun isEncrypted(value: String?): Boolean = value == null || localEncryption.isEncrypted(value)

        private fun logRepair(event: SensitiveRepairEvent) {
            loggingPort.warning(
                TAG,
                "Sensitive persistence self-heal owner=${event.ref.owner} field=${event.ref.field} " +
                    "boundary=${event.ref.boundary} entity=${event.entityId} source=${event.sourceSide} action=${event.action}",
            )
        }

        private companion object {
            const val TAG = "SensitiveSelfHeal"
        }
    }
