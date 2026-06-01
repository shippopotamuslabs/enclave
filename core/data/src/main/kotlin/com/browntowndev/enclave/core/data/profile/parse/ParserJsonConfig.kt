package com.browntowndev.enclave.core.data.profile.parse

import kotlinx.serialization.json.Json

/** Shared lenient Json instance for all export parsers. */
internal val parserJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}
