package com.browntowndev.pocketcrew.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var latestIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        latestIntent = intent

        // Gatekeeper: Keeps the native splash screen visible while state is Loading
        splashScreen.setKeepOnScreenCondition {
            viewModel.startupState.value is AppStartupState.Loading
        }

        setContent {
            val state by viewModel.startupState.collectAsStateWithLifecycle()

            when (val currentState = state) {
                is AppStartupState.Loading -> {
                    // Placeholder while splash screen transition completes
                    Box(modifier = Modifier.Companion.fillMaxSize())
                }
                is AppStartupState.Ready -> {
                    // App renders only after initialRoute is verified
                    PocketCrewApp(
                        initialRoute = currentState.initialRoute,
                        modelsResult = currentState.modelsResult,
                        errorMessage = currentState.errorMessage,
                        navigationIntent = latestIntent,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntent = intent
    }
}
