package com.browntowndev.enclave.core.data.remote

import com.browntowndev.enclave.core.data.security.PersistenceBoundary
import com.browntowndev.enclave.core.data.security.SensitiveFieldCatalog
import com.browntowndev.enclave.core.data.security.SensitiveFieldRef
import com.browntowndev.enclave.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.enclave.core.data.security.SensitiveRepairAction
import com.browntowndev.enclave.core.data.security.SensitiveRepairEvent
import com.google.firebase.firestore.FieldValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSensitiveFieldRepairer
    @Inject
    constructor(
        private val catalog: SensitiveFieldCatalog,
        private val healer: SensitivePersistenceSelfHealer,
    ) {
        private val events = mutableListOf<SensitiveRepairEvent>()

        fun repairOutbound(
            collectionPath: String,
            entityId: String,
            data: Map<String, Any?>,
        ): Map<String, Any?> = repairMap(collectionPath = collectionPath, entityId = entityId, data = data, sourceSide = "ROOM")

        fun repairInbound(
            collectionPath: String,
            entityId: String,
            data: Map<String, Any?>,
        ): Map<String, Any?> {
            if (collectionPath == "api_keys" && data.containsKey("api_key")) {
                events +=
                    SensitiveRepairEvent(
                        ref = SensitiveFieldRef("ApiKeyManager", "api_key", PersistenceBoundary.FIRESTORE, collectionPath),
                        action = SensitiveRepairAction.REJECTED_UNSUPPORTED_FIELD,
                        entityId = entityId,
                        sourceSide = "FIRESTORE",
                    )
                return data - "api_key"
            }
            return repairMap(collectionPath = collectionPath, entityId = entityId, data = data, sourceSide = "FIRESTORE")
        }

        fun sanitizedEvents(): List<SensitiveRepairEvent> = events.toList()

        private fun repairMap(
            collectionPath: String,
            entityId: String,
            data: Map<String, Any?>,
            sourceSide: String,
        ): Map<String, Any?> {
            val fields = catalog.firestoreFields(collectionPath)
            if (fields.isEmpty()) return data

            return data.mapValues { (field, value) ->
                if (field !in fields) return@mapValues value
                val stringValue = value as? String ?: return@mapValues value
                val ref = FirestoreSensitiveFieldRefMapping.sensitiveFieldRef(collectionPath, field)
                val result =
                    healer.repairString(
                        ref = ref,
                        entityId = entityId,
                        value = stringValue,
                        sourceSide = sourceSide,
                    )
                result.event?.let(events::add)
                result.value ?: FieldValue.delete()
            }
        }
    }
