package com.browntowndev.enclave.feature.inference

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.browntowndev.enclave.domain.model.telemetry.TelemetryClientBuild
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import com.browntowndev.enclave.feature.inference.telemetry.ProviderCostEstimator
import com.browntowndev.enclave.feature.inference.telemetry.ProviderPricingCatalog
import com.browntowndev.enclave.feature.inference.telemetry.ProviderTelemetryNormalizer
import com.browntowndev.enclave.feature.inference.telemetry.TELEMETRY_SCHEMA_VERSION
import com.browntowndev.enclave.feature.inference.telemetry.TelemetryClientBuildProvider
import io.mockk.every
import io.mockk.mockk

internal fun testInferenceTelemetrySupport(): InferenceTelemetrySupport {
    val context = mockk<Context>(relaxed = true)
    val packageManager = mockk<PackageManager>(relaxed = true)
    val packageInfo =
        PackageInfo().apply {
            versionName = "test"
            versionCode = 1
        }
    every { context.packageName } returns "com.browntowndev.enclave.test"
    every { context.packageManager } returns packageManager
    every { packageManager.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo
    val buildProvider = TelemetryClientBuildProvider(context)
    return InferenceTelemetrySupport(
        normalizer = ProviderTelemetryNormalizer(ProviderCostEstimator(ProviderPricingCatalog())),
        clientBuildProvider = buildProvider,
    )
}

internal fun testTelemetryClientBuild(): TelemetryClientBuild =
    TelemetryClientBuild(
        appVersionName = "test",
        appVersionCode = 1L,
        telemetrySchemaVersion = TELEMETRY_SCHEMA_VERSION,
    )
