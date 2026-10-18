package com.browntowndev.enclave.feature.inference

import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.domain.model.telemetry.TelemetryClientBuild
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import com.browntowndev.enclave.feature.inference.telemetry.ProviderCostEstimator
import com.browntowndev.enclave.feature.inference.telemetry.ProviderTelemetryNormalizer
import com.browntowndev.enclave.feature.inference.telemetry.SavedApiModelPricingEstimator
import com.browntowndev.enclave.feature.inference.telemetry.TELEMETRY_SCHEMA_VERSION
import com.browntowndev.enclave.feature.inference.telemetry.TelemetryClientBuildProvider
import org.robolectric.Shadows

/**
 * Real [InferenceTelemetrySupport] over a Robolectric application context whose
 * package info reports versionName "test" / versionCode 1 (ENC-28: Android
 * framework types are never mocked; callers must run under Robolectric).
 */
internal fun testInferenceTelemetrySupport(): InferenceTelemetrySupport {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val packageInfo =
        PackageInfo().apply {
            packageName = context.packageName
            versionName = "test"
            @Suppress("DEPRECATION")
            versionCode = 1
        }
    Shadows.shadowOf(context.packageManager).installPackage(packageInfo)
    val buildProvider = TelemetryClientBuildProvider(context)
    return InferenceTelemetrySupport(
        normalizer = ProviderTelemetryNormalizer(ProviderCostEstimator(SavedApiModelPricingEstimator())),
        clientBuildProvider = buildProvider,
    )
}

internal fun testTelemetryClientBuild(): TelemetryClientBuild =
    TelemetryClientBuild(
        appVersionName = "test",
        appVersionCode = 1L,
        telemetrySchemaVersion = TELEMETRY_SCHEMA_VERSION,
    )
