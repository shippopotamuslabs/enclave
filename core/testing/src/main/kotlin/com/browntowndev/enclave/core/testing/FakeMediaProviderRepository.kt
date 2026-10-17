package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [MediaProviderRepositoryPort] fake (ENC-28). Records every save for assertions. */
class FakeMediaProviderRepository(
    initial: List<MediaProviderAsset> = emptyList(),
) : MediaProviderRepositoryPort {
    private val state = MutableStateFlow(initial)

    /** Every (asset, apiKey) pair passed to [saveMediaProvider], in call order. */
    val savedCalls = mutableListOf<Pair<MediaProviderAsset, String?>>()

    /** Replaces all providers at once (drives getMediaProviders emissions). */
    fun emitProviders(providers: List<MediaProviderAsset>) {
        state.value = providers
    }

    override fun getMediaProviders(): Flow<List<MediaProviderAsset>> = state.asStateFlow()

    override suspend fun getMediaProvidersSync(): List<MediaProviderAsset> = state.value

    override suspend fun getMediaProvider(id: MediaProviderId): MediaProviderAsset? =
        state.value.firstOrNull { it.id == id }

    override suspend fun saveMediaProvider(asset: MediaProviderAsset, apiKey: String?): MediaProviderId {
        savedCalls += asset to apiKey
        state.value = state.value.filterNot { it.id == asset.id } + asset
        return asset.id
    }

    override suspend fun deleteMediaProvider(id: MediaProviderId) {
        state.value = state.value.filterNot { it.id == id }
    }
}
