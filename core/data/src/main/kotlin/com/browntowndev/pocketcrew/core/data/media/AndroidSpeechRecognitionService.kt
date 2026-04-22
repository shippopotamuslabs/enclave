package com.browntowndev.pocketcrew.core.data.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.browntowndev.pocketcrew.domain.port.media.SpeechRecognitionPort
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSpeechRecognitionService @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechRecognitionPort {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
    override val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningManually = false
    private var accumulatedText = ""

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _speechState.value = SpeechState.Listening
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            if (!isListeningManually) {
                // If we are not manually listening, it means the session was aborted by the user.
                // We ignore any errors (like ERROR_CLIENT or ERROR_NO_MATCH) that arrive
                // asynchronously after cancellation.
                return
            }

            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                11 -> "Server disconnected" // ERROR_SERVER_DISCONNECTED
                12 -> "Language not supported" // ERROR_LANGUAGE_NOT_SUPPORTED
                13 -> "Language unavailable" // ERROR_LANGUAGE_UNAVAILABLE
                else -> "Unknown error: $error"
            }

            if (isListeningManually && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                // Natural silence timeout - restart internally
                startListeningInternal()
            } else {
                isListeningManually = false
                _speechState.value = SpeechState.Error(message)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val newText = matches[0]
                accumulatedText = if (accumulatedText.isBlank()) newText else "$accumulatedText $newText"
                _speechState.value = SpeechState.FinalText(accumulatedText.trim())
            }

            if (isListeningManually) {
                // Ensure the previous loop is definitively finished by calling cancel()
                // before restarting, preventing state leaks or premature Listening emissions.
                speechRecognizer?.cancel()
                startListeningInternal()
            } else {
                _speechState.value = SpeechState.Idle
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val partial = matches[0]
                val fullText = if (accumulatedText.isBlank()) partial else "$accumulatedText $partial"
                _speechState.value = SpeechState.PartialText(fullText.trim())
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun startListening(initialText: String) {
        isListeningManually = true
        accumulatedText = initialText
        startListeningInternal()
    }

    private fun startListeningInternal() {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                isListeningManually = false
                _speechState.value = SpeechState.Error("No speech recognition service found on this device.")
                return@post
            }

            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(recognitionListener)
                }
            } else {
                speechRecognizer?.cancel() // Reset any previous state without destroying binder
            }

            // Don't overwrite state if we are already in PartialText or Listening from a previous loop
            if (_speechState.value !is SpeechState.PartialText) {
                _speechState.value = SpeechState.Listening
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
        }
    }

    override fun stopListening() {
        isListeningManually = false
        accumulatedText = ""
        mainHandler.post {
            speechRecognizer?.cancel()
            _speechState.value = SpeechState.Idle
        }
    }
}
