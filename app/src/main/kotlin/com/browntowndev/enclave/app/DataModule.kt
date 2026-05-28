package com.browntowndev.enclave.app

import android.app.NotificationManager
import android.content.Context
import com.browntowndev.enclave.BuildConfig
import com.browntowndev.enclave.core.data.repository.TelemetryEventRepositoryImpl
import com.browntowndev.enclave.core.data.sync.TelemetryUploadConfig
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.app.telemetry.TelemetryLoggingAdapter
import com.browntowndev.enclave.domain.qualifier.InternalTelemetryLogging
import com.browntowndev.enclave.feature.inference.AndroidLoggingAdapter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context,
    ): NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Provides
    @Singleton
    fun provideTelemetryUploadConfig(): TelemetryUploadConfig {
        val endpoint = BuildConfig.TELEMETRY_OTLP_ENDPOINT.trim()
        val apiKey = BuildConfig.TELEMETRY_OTLP_API_KEY.trim()
        return TelemetryUploadConfig(
            endpointUrl = endpoint.takeIf { it.isNotEmpty() },
            apiKey = apiKey.takeIf { it.isNotEmpty() },
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingModule {
    @Binds
    @Singleton
    abstract fun bindLoggingPort(telemetryLoggingAdapter: TelemetryLoggingAdapter): LoggingPort

    @Binds
    @Singleton
    @InternalTelemetryLogging
    abstract fun bindInternalTelemetryLoggingPort(androidLoggingAdapter: AndroidLoggingAdapter): LoggingPort

    @Binds
    @Singleton
    abstract fun bindTelemetryEventPort(impl: TelemetryEventRepositoryImpl): TelemetryEventPort
}
