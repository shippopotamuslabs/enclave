package com.browntowndev.pocketcrew

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import com.browntowndev.pocketcrew.core.docgen.RustDocGenSchemaProvider
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.auth.AuthManager
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.sync.CloudSyncAuthListener
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.qualifier.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PocketCrewApplication :
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

    override fun newImageLoader(context: android.content.Context): ImageLoader = imageLoader

    override fun onCreate() {
        super.onCreate()

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
