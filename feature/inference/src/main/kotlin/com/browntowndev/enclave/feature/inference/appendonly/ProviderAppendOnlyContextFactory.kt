package com.browntowndev.enclave.feature.inference.appendonly

internal object ProviderAppendOnlyContextFactory {
    fun create(onLogCleared: ((reason: String) -> Unit)? = null): ProviderAppendOnlyContext =
        ProviderAppendOnlyContext(onLogCleared = onLogCleared)
}
