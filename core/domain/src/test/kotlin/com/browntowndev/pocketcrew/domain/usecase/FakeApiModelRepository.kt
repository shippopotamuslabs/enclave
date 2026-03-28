package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfig
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeApiModelRepository : ApiModelRepositoryPort {
    private val _models = MutableStateFlow<List<ApiModelConfig>>(emptyList())
    private var nextId = 1L
    val savedKeys = mutableMapOf<Long, String>()

    override fun observeAll(): Flow<List<ApiModelConfig>> = _models
    override suspend fun getAll(): List<ApiModelConfig> = _models.value
    override suspend fun getById(id: Long): ApiModelConfig? = _models.value.find { it.id == id }

    override suspend fun save(config: ApiModelConfig, apiKey: String): Long {
        val id = if (config.id == 0L) nextId++ else config.id
        val saved = config.copy(id = id)
        _models.value = _models.value.filter { it.id != id } + saved
        savedKeys[id] = apiKey
        return id
    }

    override suspend fun delete(id: Long) {
        _models.value = _models.value.filter { it.id != id }
        savedKeys.remove(id)
    }
}
