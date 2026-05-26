package com.browntowndev.enclave.domain.usecase

import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeUtilityModelRepository : UtilityModelRepositoryPort {
    private val rows = mutableMapOf<UtilityType, UtilityModelMetadata>()
    var lastUpserted: UtilityModelMetadata? = null

    override suspend fun getByUtilityType(utilityType: UtilityType): UtilityModelMetadata? = rows[utilityType]

    override suspend fun getAllActive(): List<UtilityModelMetadata> = rows.values.toList()

    override fun observeAllActive(): Flow<List<UtilityModelMetadata>> = MutableStateFlow(rows.values.toList()).map { it }

    override suspend fun getById(id: UtilityModelId): UtilityModelMetadata? = rows.values.firstOrNull { it.id == id }

    override suspend fun getBySha256(sha256: String): UtilityModelMetadata? = rows.values.firstOrNull { it.sha256 == sha256 }

    override suspend fun upsert(metadata: UtilityModelMetadata): UtilityModelId {
        lastUpserted = metadata
        val id =
            if (metadata.id.value.isNotEmpty()) {
                metadata.id
            } else {
                val existing = rows[metadata.utilityType]
                existing?.id ?: UtilityModelId(
                    java.util.UUID
                        .randomUUID()
                        .toString(),
                )
            }
        rows[metadata.utilityType] = metadata.copy(id = id)
        return id
    }

    override suspend fun softDelete(id: UtilityModelId) {
        rows.entries.removeIf { it.value.id == id }
    }
}
