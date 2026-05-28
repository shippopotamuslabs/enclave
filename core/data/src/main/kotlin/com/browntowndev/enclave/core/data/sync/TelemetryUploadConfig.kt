package com.browntowndev.enclave.core.data.sync

/**
 * Grafana Cloud OTLP upload configuration. Upload is disabled until both values are present.
 */
data class TelemetryUploadConfig(
    val endpointUrl: String? = null,
    val apiKey: String? = null,
) {
    val isConfigured: Boolean
        get() = !endpointUrl.isNullOrBlank() && !apiKey.isNullOrBlank()
}
