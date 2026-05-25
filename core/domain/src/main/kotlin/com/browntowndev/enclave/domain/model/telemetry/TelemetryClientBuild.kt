package com.browntowndev.enclave.domain.model.telemetry

data class TelemetryClientBuild(
    val appVersionName: String,
    val appVersionCode: Long,
    val telemetrySchemaVersion: Int,
)
