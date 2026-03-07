package com.browntowndev.pocketcrew.data.download

import com.browntowndev.pocketcrew.domain.model.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.port.cache.ModelConfigCachePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelConfigCacheImpl @Inject constructor(
    private val modelRegistry: ModelRegistryPort,
    private val logPort: LoggingPort
) : ModelConfigCachePort {
    private val mutex = Mutex()

    @Volatile
    private var cache: Map<ModelType, ModelConfiguration> = emptyMap()

    override val fullConfig: List<ModelConfiguration> get() = cache.values.toList()

    @Volatile
    private var initialized = false

    /**
     * Initialize cache from registry (legacy method - used when no remote config is available).
     * This loads what's actually downloaded from the registry.
     */
    override suspend fun initialize() {
        mutex.withLock {
            if (initialized) return@withLock

            val loadedConfig = mutableMapOf<ModelType, ModelConfiguration>()
            for (type in ModelType.entries) {
                val config = modelRegistry.getRegisteredModel(type)
                if (config != null) {
                    loadedConfig[type] = config
                }
            }
            cache = loadedConfig
            initialized = true
        }
        logPort.debug("ModelConfigCache", "Initialized from registry: ${cache.values}")
    }

    /**
     * Initialize cache directly with remote config.
     * This is the preferred method - cache holds the expected remote configuration,
     * not what's in the registry.
     */
    override suspend fun initializeWithRemoteConfig(remoteConfigs: List<ModelConfiguration>) {
        mutex.withLock {
            cache = remoteConfigs.associateBy { it.modelType }
            initialized = true
        }
        logPort.debug("ModelConfigCache", "Initialized with remote config: ${cache.values}")
    }

    override fun isInitialized(): Boolean = initialized

    override fun getMainConfig(): ModelConfiguration? = cache[ModelType.MAIN]
    override fun getVisionConfig(): ModelConfiguration? = cache[ModelType.VISION]
    override fun getDraftConfig(): ModelConfiguration? = cache[ModelType.DRAFT]
    override fun getFastConfig(): ModelConfiguration? = cache[ModelType.FAST]
}
