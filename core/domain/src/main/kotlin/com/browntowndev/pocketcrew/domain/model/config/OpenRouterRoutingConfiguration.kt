package com.browntowndev.pocketcrew.domain.model.config

enum class OpenRouterProviderSort(val wireValue: String, val displayName: String) {
    PRICE("price", "Price"),
    LATENCY("latency", "Latency"),
    THROUGHPUT("throughput", "Throughput");

    companion object {
        fun fromWireValue(value: String?): OpenRouterProviderSort =
            entries.firstOrNull { it.wireValue == value } ?: THROUGHPUT
    }
}

enum class OpenRouterDataCollectionPolicy(val wireValue: String, val displayName: String) {
    ALLOW("allow", "Allow"),
    DENY("deny", "Deny");

    companion object {
        fun fromWireValue(value: String?): OpenRouterDataCollectionPolicy =
            entries.firstOrNull { it.wireValue == value } ?: DENY
    }
}

data class OpenRouterRoutingConfiguration(
    val providerSort: OpenRouterProviderSort = OpenRouterProviderSort.THROUGHPUT,
    val allowFallbacks: Boolean = true,
    val requireParameters: Boolean = false,
    val dataCollectionPolicy: OpenRouterDataCollectionPolicy = OpenRouterDataCollectionPolicy.DENY,
    val zeroDataRetention: Boolean = false,
)
