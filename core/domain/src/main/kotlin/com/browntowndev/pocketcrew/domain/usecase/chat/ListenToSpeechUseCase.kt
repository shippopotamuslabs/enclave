package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.config.UtilityType
import com.browntowndev.pocketcrew.domain.port.inference.WhisperInferencePort
import com.browntowndev.pocketcrew.domain.port.media.AudioCapturePort
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.domain.port.repository.UtilityModelFilePort
import com.browntowndev.pocketcrew.domain.util.Clock
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

class ListenToSpeechUseCase @Inject constructor(
    private val audioCapturePort: AudioCapturePort,
    private val whisperInferencePort: WhisperInferencePort,
    private val utilityModelFilePort: UtilityModelFilePort,
    private val clock: Clock,
) {
    operator fun invoke(
        initialText: String = "",
        stopSignal: StateFlow<Boolean>
    ): Flow<SpeechState> = channelFlow {
        send(SpeechState.ModelLoading)

        var initialized = false
        val utteranceBuffer = mutableListOf<Float>()
        var speechActive = false
        var lastSpeechMs = 0L
        val startTimeMs = clock.currentTimeMillis()
        var loopActive = true

        try {
            val modelPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.WHISPER)
                ?: throw IllegalStateException("Whisper model is not downloaded.")

            whisperInferencePort.initialize(modelPath)
            initialized = true
            send(SpeechState.Listening(0f, clock.currentTimeMillis()))

            audioCapturePort.audioChunks()
                .takeWhile { loopActive && !stopSignal.value }
                .collect { chunk ->
                    val now = clock.currentTimeMillis()
                    val energy = chunk.calculateEnergy()
                    
                    send(SpeechState.Listening(energy.rms, now))

                    if (energy.hasSpeech) {
                        speechActive = true
                        lastSpeechMs = now
                    }

                    if (speechActive) {
                        utteranceBuffer.append(chunk)

                        if (lastSpeechMs > 0L && now - lastSpeechMs > SILENCE_FINALIZATION_MS) {
                            loopActive = false
                        }
                    } else if (now - startTimeMs > SILENCE_FINALIZATION_MS) {
                        loopActive = false
                    }
                }

            // Perform final transcription on the entire buffer
            if (utteranceBuffer.isNotEmpty()) {
                send(SpeechState.Transcribing)
                val finalTranscript = whisperInferencePort.transcribe(utteranceBuffer.toFloatArray()).trim()
                if (finalTranscript.isNotBlank()) {
                    send(SpeechState.FinalText(finalTranscript.withInitialText(initialText)))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            send(SpeechState.Error(e.message ?: "Speech transcription failed."))
        } finally {
            if (initialized) {
                whisperInferencePort.close()
            }
            trySend(SpeechState.Idle)
        }
    }

    private fun FloatArray.calculateEnergy(): AudioEnergy {
        if (isEmpty()) return AudioEnergy(false, 0f)

        var absoluteSum = 0.0
        var squareSum = 0.0
        for (sample in this) {
            val value = sample.toDouble()
            absoluteSum += kotlin.math.abs(value)
            squareSum += value * value
        }

        val averageAmplitude = absoluteSum / size
        val rootMeanSquare = kotlin.math.sqrt(squareSum / size)
        val hasSpeech = averageAmplitude >= SPEECH_AMPLITUDE_THRESHOLD || rootMeanSquare >= SPEECH_RMS_THRESHOLD
        
        return AudioEnergy(hasSpeech, rootMeanSquare.toFloat())
    }

    private data class AudioEnergy(val hasSpeech: Boolean, val rms: Float)

    private fun MutableList<Float>.append(samples: FloatArray) {
        for (sample in samples) {
            add(sample)
        }
    }

    private fun String.withInitialText(initialText: String): String {
        return when {
            initialText.isBlank() -> this
            isBlank() -> initialText.trim()
            else -> "${initialText.trim()} $this"
        }
    }

    private companion object {
        const val SILENCE_FINALIZATION_MS = 10_000L
        const val SPEECH_AMPLITUDE_THRESHOLD = 0.015
        const val SPEECH_RMS_THRESHOLD = 0.02
    }
}
