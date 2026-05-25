package com.browntowndev.enclave.feature.inference.telemetry

import android.content.Context
import com.browntowndev.enclave.domain.model.telemetry.TelemetryClientBuild
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetryClientBuildProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun current(): TelemetryClientBuild {
            val packageInfo =
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }.getOrNull()
            return TelemetryClientBuild(
                appVersionName = packageInfo?.versionName ?: "unknown",
                appVersionCode = packageInfo?.longVersionCode ?: 0L,
                telemetrySchemaVersion = TELEMETRY_SCHEMA_VERSION,
            )
        }
    }

const val TELEMETRY_SCHEMA_VERSION = 1
