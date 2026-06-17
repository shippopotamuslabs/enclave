package com.browntowndev.enclave.domain.usecase

import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeUtilityModelRepository : UtilityModelRepositoryPort {
    private val _store = MutableStateFlow<Map<UtilityModelId, UtilityModelMetadata>>(emptyMap())
    var lastUpserted: UtilityModelMetadata? = null
        private set

    override suspend fun getByUtilityType(utilityType: UtilityType): UtilityModelMetadata? =
        _store.value.values.firstOrNull { it.utilityType == utilityType }

    override suspend fun getAllActive(): List<UtilityModelMetadata> = _store.value.values.toList()

    override fun observeAllActive(): Flow<List<UtilityModelMetadata>> =
        _store.asStateFlow().map { it.values.toList() }

    override suspend fun getById(id: UtilityModelId): UtilityModelMetadata? = _store.value[id]

    override suspend fun getBySha256(sha256: String): UtilityModelMetadata? =
        _store.value.values.firstOrNull { it.sha256 == sha256 }

    override suspend fun upsert(metadata: UtilityModelMetadata): UtilityModelId {
        val existing = _store.value.entries.firstOrNull { it.value.utilityType == metadata.utilityType }
        val id = existing?.key ?: UtilityModelId(UUID.randomUUID().toString())
        val stored = metadata.copy(id = id)
        _store.value = _store.value.toMutableMap().also { it[id] = stored }
        lastUpserted = stored
        return id
    }

    override suspend fun softDelete(id: UtilityModelId) {
        _store.value = _store.value.toMutableMap().also { it.remove(id) }
    }
}
