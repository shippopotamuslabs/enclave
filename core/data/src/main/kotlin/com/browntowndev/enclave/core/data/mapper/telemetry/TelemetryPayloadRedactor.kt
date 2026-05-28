package com.browntowndev.enclave.core.data.mapper.telemetry

import com.browntowndev.enclave.domain.util.telemetry.TelemetryPiiScrubber

internal object TelemetryPayloadRedactor {
    fun redact(value: String?): String? = TelemetryPiiScrubber.scrub(value)
}
