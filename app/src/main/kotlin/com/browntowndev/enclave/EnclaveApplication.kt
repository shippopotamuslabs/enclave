package com.browntowndev.enclave

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import com.browntowndev.enclave.core.docgen.RustDocGenSchemaProvider
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.auth.AuthManager
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.core.data.sync.TelemetryUploadScheduler
import com.browntowndev.enclave.domain.qualifier.ApplicationScope
import com.browntowndev.enclave.sync.CloudSyncAuthListener
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EnclaveApplication :
    Application(),
    Configuration.Provider,
    coil3.SingletonImageLoader.Factory {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var authManager: AuthManager

    @Inject
    lateinit var cloudSyncPort: CloudSyncPort

    @Inject
    lateinit var cryptoManager: CryptoManagerPort

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var toolEnvelopeParserFacadeInstaller: ToolEnvelopeParserFacadeInstaller

    @Inject
    lateinit var telemetryUploadScheduler: TelemetryUploadScheduler

    override fun newImageLoader(context: android.content.Context): ImageLoader = imageLoader

    override fun onCreate() {
        super.onCreate()

        telemetryUploadScheduler.schedule()

        toolEnvelopeParserFacadeInstaller.install()
        ToolDefinition.schemaProvider = RustDocGenSchemaProvider()

        applicationScope.launch {
            combine(authManager.authState, cryptoManager.isVaultUnlocked) { authState, isUnlocked ->
                authState to isUnlocked
            }.collect { (authState, isUnlocked) ->
                CloudSyncAuthListener.onAuthAndVaultStateChanged(
                    authState = authState,
                    isVaultUnlocked = isUnlocked,
                    cloudSyncPort = cloudSyncPort,
                )
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
