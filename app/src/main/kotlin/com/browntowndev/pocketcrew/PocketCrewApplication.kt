package com.browntowndev.pocketcrew

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.ai.edge.litertlm.ExperimentalFlags
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PocketCrewApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @OptIn(com.google.ai.edge.litertlm.ExperimentalApi::class)
    override fun onCreate() {
        super.onCreate()
        
        // Required for LiteRT NPU support if available on the device
        // This is a static initialization that only needs to happen once.
        ExperimentalFlags.npuLibrariesDir = applicationInfo.nativeLibraryDir
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}