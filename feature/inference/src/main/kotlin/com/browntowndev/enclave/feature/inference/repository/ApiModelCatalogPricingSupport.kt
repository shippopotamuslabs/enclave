package com.browntowndev.enclave.feature.inference.repository

import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

internal const val CATALOG_PER_TOKEN_TO_PER_MILLION = 1_000_000.0
internal const val CATALOG_XAI_PER_TOKEN_TO_PER_MILLION = 10_000.0

internal fun Double.asUsdPerMillionFromPerToken(): Double? =
    takeIf { isFinite() && this >= 0.0 }
        ?.times(CATALOG_PER_TOKEN_TO_PER_MILLION)

internal fun String.toPerTokenUsdPerMillion(): Double? =
    toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0.0 }
        ?.let { perToken ->
            java.math.BigDecimal.valueOf(perToken)
                .multiply(java.math.BigDecimal.valueOf(CATALOG_PER_TOKEN_TO_PER_MILLION))
                .stripTrailingZeros()
                .toDouble()
        }

internal fun Double.asUsdPerMillionFromXai(): Double? =
    takeIf { isFinite() && this >= 0.0 }
        ?.div(CATALOG_XAI_PER_TOKEN_TO_PER_MILLION)

internal fun String.toEpochSecondOrNull(): Long? =
    try {
        OffsetDateTime.parse(this).toEpochSecond()
    } catch (_: DateTimeParseException) {
        null
    }
