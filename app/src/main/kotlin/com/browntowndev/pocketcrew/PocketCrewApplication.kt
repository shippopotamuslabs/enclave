package com.browntowndev.pocketcrew
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.ai.edge.litertlm.ExperimentalApi
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PocketCrewApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @OptIn(ExperimentalApi::class)
    override fun onCreate() {
        super.onCreate()

        // Required for LiteRT NPU support if available on the device
        // This is a static initialization that only needs to happen once.
        // Removed as of litertlm 0.10.0, NPU path handling has changed internally
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
