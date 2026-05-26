package com.browntowndev.enclave.domain.model.config

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object ApiModelPricingFormat {
    fun formatUsdPerMillion(value: Double): String? {
        if (!value.isFinite() || value < 0) return null
        val formatter =
            DecimalFormat("#,##0.####", DecimalFormatSymbols.getInstance(Locale.US)).apply {
                minimumFractionDigits = 0
                maximumFractionDigits = 4
            }
        return formatter.format(value)
    }

    fun parseUsdPerMillion(input: String): Double? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val normalized = trimmed.replace(",", "")
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }

    fun displayUsdPerMillion(value: Double?): String = value?.let(::formatUsdPerMillion)?.let { "$$it / 1M tokens" } ?: "—"
}
