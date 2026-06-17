package com.browntowndev.enclave.core.data.media

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.media.VoiceChatAudioRoutePort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidVoiceChatAudioRoute
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val loggingPort: LoggingPort,
    ) : VoiceChatAudioRoutePort {
        private val audioManager: AudioManager =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        @Volatile
        private var isActive: Boolean = false

        private val configurationCallbacks =
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    if (isActive) {
                        applySpeakerphoneRoute()
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onLowMemory() = Unit
            }

        init {
            context.registerComponentCallbacks(configurationCallbacks)
        }

        override fun activateSpeakerphone() {
            isActive = true
            applySpeakerphoneRoute()
        }

        override fun deactivateSpeakerphone() {
            isActive = false
            restoreDefaultRoute()
        }

        private fun applySpeakerphoneRoute() {
            runCatching {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                val speakerDevice =
                    audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                if (speakerDevice != null) {
                    val routed = audioManager.setCommunicationDevice(speakerDevice)
                    loggingPort.debug(TAG, "Speakerphone route applied: routed=$routed")
                } else {
                    loggingPort.debug(TAG, "No built-in speaker communication device; keeping platform default")
                }
            }.onFailure { error ->
                loggingPort.error(TAG, "Failed to activate speakerphone route: ${error.message}")
            }
        }

        private fun restoreDefaultRoute() {
            runCatching {
                audioManager.clearCommunicationDevice()
                audioManager.mode = AudioManager.MODE_NORMAL
                loggingPort.debug(TAG, "Speakerphone route cleared")
            }.onFailure { error ->
                loggingPort.error(TAG, "Failed to deactivate speakerphone route: ${error.message}")
            }
        }

        private companion object {
            const val TAG = "AndroidVoiceChatAudioRoute"
        }
    }
