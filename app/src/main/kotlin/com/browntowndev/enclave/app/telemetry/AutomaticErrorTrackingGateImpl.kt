package com.browntowndev.enclave.app.telemetry

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.browntowndev.enclave.domain.model.settings.SettingsPreferenceKeys
import com.browntowndev.enclave.domain.port.telemetry.AutomaticErrorTrackingGate
import com.browntowndev.enclave.domain.qualifier.ApplicationScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private val automaticErrorTrackingKey =
    booleanPreferencesKey(SettingsPreferenceKeys.AUTOMATIC_ERROR_TRACKING_ENABLED)

@Singleton
class AutomaticErrorTrackingGateImpl
    @Inject
    constructor(
        dataStore: DataStore<Preferences>,
        @ApplicationScope applicationScope: CoroutineScope,
    ) : AutomaticErrorTrackingGate {
        private val enabled = AtomicBoolean(false)

        init {
            dataStore.data
                .map { preferences -> preferences[automaticErrorTrackingKey] ?: false }
                .distinctUntilChanged()
                .onEach { isEnabled -> enabled.set(isEnabled) }
                .launchIn(applicationScope)
        }

        override fun isAutomaticErrorTrackingEnabled(): Boolean = enabled.get()
    }

@Singleton
class AutomaticErrorTrackingCoordinator
    @Inject
    constructor(
        dataStore: DataStore<Preferences>,
        @ApplicationScope applicationScope: CoroutineScope,
    ) {
        init {
            dataStore.data
                .map { preferences -> preferences[automaticErrorTrackingKey] ?: false }
                .distinctUntilChanged()
                .onEach { isEnabled ->
                    runCatching {
                        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(isEnabled)
                    }
                }.launchIn(applicationScope)
        }
    }
