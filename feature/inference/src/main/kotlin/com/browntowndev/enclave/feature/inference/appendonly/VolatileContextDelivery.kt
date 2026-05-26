package com.browntowndev.enclave.feature.inference.appendonly

internal sealed interface VolatileContextDelivery {
    data class OpenAiTailBeforeLastSessionMessage(
        val sessionLastIndex: Int,
    ) : VolatileContextDelivery

    data object PrependToLatestUserContent : VolatileContextDelivery

    data object MergeIntoSystemPrompt : VolatileContextDelivery
}
