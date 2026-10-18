package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.config.TtsProviderAsset
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.port.repository.TtsProviderRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [TtsProviderRepositoryPort] fake (ENC-28). Records every save for assertions. */
class FakeTtsProviderRepository(
    initial: List<TtsProviderAsset> = emptyList(),
) : TtsProviderRepositoryPort {
    private val state = MutableStateFlow(initial)

    /** Every (asset, apiKey) pair passed to [saveTtsProvider], in call order. */
    val savedCalls = mutableListOf<Pair<TtsProviderAsset, String?>>()

    /** Replaces all providers at once (drives getTtsProviders emissions) (ENC-28). */
    fun emit(providers: List<TtsProviderAsset>) {
        state.value = providers
    }

    override fun getTtsProviders(): Flow<List<TtsProviderAsset>> = state.asStateFlow()

    override suspend fun getTtsProvidersSync(): List<TtsProviderAsset> = state.value

    override suspend fun getTtsProvider(id: TtsProviderId): TtsProviderAsset? =
        state.value.firstOrNull { it.id == id }

    override suspend fun saveTtsProvider(asset: TtsProviderAsset, apiKey: String?): TtsProviderId {
        savedCalls += asset to apiKey
        state.value = state.value.filterNot { it.id == asset.id } + asset
        return asset.id
    }

    override suspend fun deleteTtsProvider(id: TtsProviderId) {
        state.value = state.value.filterNot { it.id == id }
    }
}
