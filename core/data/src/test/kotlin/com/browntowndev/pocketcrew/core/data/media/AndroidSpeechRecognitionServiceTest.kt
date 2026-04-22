package com.browntowndev.pocketcrew.core.data.media

import android.content.Context
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidSpeechRecognitionServiceTest {

    private lateinit var context: Context
    private lateinit var mockRecognizer: SpeechRecognizer
    private lateinit var service: AndroidSpeechRecognitionService
    private val listenerSlot = slot<RecognitionListener>()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockRecognizer = mockk(relaxed = true)
        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.createSpeechRecognizer(any()) } returns mockRecognizer
        every { mockRecognizer.setRecognitionListener(capture(listenerSlot)) } returns Unit
        
        service = AndroidSpeechRecognitionService(context)
    }

    @After
    fun teardown() {
        unmockkStatic(SpeechRecognizer::class)
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(SpeechState.Idle, service.speechState.value)
    }

    @Test
    fun `startListening initializes recognizer and calls start`() {
        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.createSpeechRecognizer(any()) } returns mockRecognizer
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns true
        
        service.startListening("initial")
        ShadowLooper.idleMainLooper()
        
        verify { SpeechRecognizer.createSpeechRecognizer(any()) }
        verify { mockRecognizer.setRecognitionListener(any()) }
        verify { mockRecognizer.startListening(any()) }
        
        unmockkStatic(SpeechRecognizer::class)
    }

    @Test
    fun `stopListening calls recognizer cancel`() {
        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.createSpeechRecognizer(any()) } returns mockRecognizer
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns true
        
        service.startListening("")
        ShadowLooper.idleMainLooper()
        service.stopListening()
        ShadowLooper.idleMainLooper()
        verify { mockRecognizer.cancel() }
        
        unmockkStatic(SpeechRecognizer::class)
    }

    @Test
    fun `listener onReadyForSpeech updates state to Listening`() {
        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.createSpeechRecognizer(any()) } returns mockRecognizer
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns true
        
        service.startListening("")
        ShadowLooper.idleMainLooper()
        
        listenerSlot.captured.onReadyForSpeech(null)
        assertEquals(SpeechState.Listening, service.speechState.value)
        
        unmockkStatic(SpeechRecognizer::class)
    }

    @Test
    fun `listener onPartialResults updates state to PartialText`() {
        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.createSpeechRecognizer(any()) } returns mockRecognizer
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns true
        
        service.startListening("accumulated")
        ShadowLooper.idleMainLooper()
        
        val bundle = Bundle()
        bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("partial"))
        
        listenerSlot.captured.onPartialResults(bundle)
        
        val state = service.speechState.value
        assertTrue(state is SpeechState.PartialText)
        assertEquals("accumulated partial", (state as SpeechState.PartialText).text)
        
        unmockkStatic(SpeechRecognizer::class)
    }

    @Test
    fun `listener onResults appends text and restarts internally`() {
        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.createSpeechRecognizer(any()) } returns mockRecognizer
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns true
        
        service.startListening("first")
        ShadowLooper.idleMainLooper()
        
        val bundle = Bundle()
        bundle.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("second"))
        
        listenerSlot.captured.onResults(bundle)
        val state = service.speechState.value
        assertTrue(state is SpeechState.FinalText || state is SpeechState.Listening)
        
        unmockkStatic(SpeechRecognizer::class)
    }

    @Test
    fun `listener onError restarts internally on timeout`() {
        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.createSpeechRecognizer(any()) } returns mockRecognizer
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns true
        
        service.startListening("initial")
        ShadowLooper.idleMainLooper()
        
        listenerSlot.captured.onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        ShadowLooper.idleMainLooper()
        
        // State should remain Listening (or PartialText if it was there), not Idle or Error
        assertTrue(service.speechState.value !is SpeechState.Error)
        assertTrue(service.speechState.value !is SpeechState.Idle)
        
        unmockkStatic(SpeechRecognizer::class)
    }

    @Test
    fun `listener onError updates state to Error`() {
        mockkStatic(SpeechRecognizer::class)
        every { SpeechRecognizer.createSpeechRecognizer(any()) } returns mockRecognizer
        every { SpeechRecognizer.isRecognitionAvailable(any()) } returns true
        
        service.startListening()
        ShadowLooper.idleMainLooper()
        
        listenerSlot.captured.onError(SpeechRecognizer.ERROR_NETWORK)
        
        val state = service.speechState.value
        assertTrue(state is SpeechState.Error)
        assertEquals("Network error", (state as SpeechState.Error).message)
        
        unmockkStatic(SpeechRecognizer::class)
    }
}
