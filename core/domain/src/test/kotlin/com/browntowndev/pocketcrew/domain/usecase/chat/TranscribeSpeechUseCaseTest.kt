package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.port.media.SpeechRecognitionPort
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TranscribeSpeechUseCaseTest {

    private lateinit var speechRecognitionPort: SpeechRecognitionPort
    private lateinit var useCase: TranscribeSpeechUseCase

    @BeforeEach
    fun setup() {
        speechRecognitionPort = mockk(relaxed = true)
        val speechStateFlow = MutableStateFlow<SpeechState>(SpeechState.Idle)
        every { speechRecognitionPort.speechState } returns speechStateFlow
        
        useCase = TranscribeSpeechUseCase(speechRecognitionPort)
    }

    @Test
    fun `speechState flow correctly exposes the port's underlying StateFlow`() {
        assertEquals(SpeechState.Idle, useCase.speechState.value)
    }

    @Test
    fun `startListening correctly delegates to SpeechRecognitionPort`() {
        useCase.startListening("initial")
        verify(exactly = 1) { speechRecognitionPort.startListening("initial") }
    }

    @Test
    fun `stopListening correctly delegates to SpeechRecognitionPort`() {
        useCase.stopListening()
        verify(exactly = 1) { speechRecognitionPort.stopListening() }
    }
}
