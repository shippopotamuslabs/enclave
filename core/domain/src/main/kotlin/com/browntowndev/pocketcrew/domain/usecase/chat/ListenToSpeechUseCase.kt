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
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

class ListenToSpeechUseCase @Inject constructor(
    private val audioCapturePort: AudioCapturePort,
    private val whisperInferencePort: WhisperInferencePort,
    private val utilityModelFilePort: UtilityModelFilePort,
    private val clock: Clock,
) {
    operator fun invoke(initialText: String = ""): Flow<SpeechState> = channelFlow {
        send(SpeechState.ModelLoading)

        var initialized = false
        var partialJob: Job? = null
        val utteranceBuffer = mutableListOf<Float>()
        var speechActive = false
        var lastSpeechMs = 0L
        var lastInferenceMs = 0L
        var lastPartialText = ""

        try {
            val modelPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.WHISPER)
                ?: throw IllegalStateException("Whisper model is not downloaded.")

            whisperInferencePort.initialize(modelPath)
            initialized = true
            send(SpeechState.Listening)

            audioCapturePort.audioChunks().collect { chunk ->
                val now = clock.currentTimeMillis()
                val hasSpeech = chunk.hasSpeechEnergy()

                if (hasSpeech) {
                    if (!speechActive) {
                        utteranceBuffer.clear()
                        lastPartialText = ""
                        lastInferenceMs = now
                        send(SpeechState.Listening)
                    }
                    speechActive = true
                    lastSpeechMs = now
                }

                if (speechActive) {
                    utteranceBuffer.append(chunk)

                    if (now - lastInferenceMs >= PARTIAL_TRANSCRIPTION_INTERVAL_MS &&
                        partialJob?.isActive != true
                    ) {
                        val snapshot = utteranceBuffer.toFloatArray()
                        lastInferenceMs = now
                        partialJob = launch {
                            val transcript = whisperInferencePort.transcribe(snapshot).trim()
                            if (transcript.isNotBlank() && transcript != lastPartialText) {
                                lastPartialText = transcript
                                send(SpeechState.PartialText(transcript.withInitialText(initialText)))
                            }
                        }
                    }

                    if (lastSpeechMs > 0L && now - lastSpeechMs > SILENCE_FINALIZATION_MS) {
                        partialJob?.cancelAndJoin()
                        partialJob = null

                        val finalTranscript = whisperInferencePort.transcribe(utteranceBuffer.toFloatArray()).trim()
                        if (finalTranscript.isNotBlank()) {
                            send(SpeechState.FinalText(finalTranscript.withInitialText(initialText)))
                        }

                        utteranceBuffer.clear()
                        speechActive = false
                        lastSpeechMs = 0L
                        lastInferenceMs = 0L
                        lastPartialText = ""
                        send(SpeechState.Listening)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            send(SpeechState.Error(e.message ?: "Speech transcription failed."))
        } finally {
            partialJob?.cancelAndJoin()
            if (initialized) {
                whisperInferencePort.close()
            }
            trySend(SpeechState.Idle)
        }
    }

    private fun FloatArray.hasSpeechEnergy(): Boolean {
        if (isEmpty()) return false

        var absoluteSum = 0.0
        var squareSum = 0.0
        for (sample in this) {
            val value = sample.toDouble()
            absoluteSum += kotlin.math.abs(value)
            squareSum += value * value
        }

        val averageAmplitude = absoluteSum / size
        val rootMeanSquare = kotlin.math.sqrt(squareSum / size)
        return averageAmplitude >= SPEECH_AMPLITUDE_THRESHOLD || rootMeanSquare >= SPEECH_RMS_THRESHOLD
    }

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
        const val PARTIAL_TRANSCRIPTION_INTERVAL_MS = 1_000L
        const val SILENCE_FINALIZATION_MS = 1_500L
        const val SPEECH_AMPLITUDE_THRESHOLD = 0.015
        const val SPEECH_RMS_THRESHOLD = 0.02
    }
}
