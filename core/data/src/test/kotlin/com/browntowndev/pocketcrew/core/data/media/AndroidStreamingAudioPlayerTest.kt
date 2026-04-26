package com.browntowndev.pocketcrew.core.data.media

import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.media.StreamingAudioConfig
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidStreamingAudioPlayerTest {

    private lateinit var player: AndroidStreamingAudioPlayer
    private val mockLogger: LoggingPort = mockk(relaxed = true)

    @Before
    fun setUp() {
        player = AndroidStreamingAudioPlayer(mockLogger)
    }

    @Test
    fun `startPlayback initializes with correct parameters`() = runBlocking {
        val config = StreamingAudioConfig.PCM_16_24KHZ_MONO

        player.initialize(config)
        player.startPlayback()

        assertTrue(player.isInitialized())
    }

    @Test(expected = IllegalStateException::class)
    fun `enqueueChunk throws when player not initialized`() = runBlocking {
        player.enqueueChunk(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `stop does nothing when player not initialized`() {
        // Should not throw
        player.stop()
        assertFalse(player.isInitialized())
    }

    @Test
    fun `enqueueChunk accepts valid PCM data`() = runBlocking {
        val config = StreamingAudioConfig.PCM_16_24KHZ_MONO
        player.initialize(config)
        player.startPlayback()

        // Should not throw for valid PCM data
        player.enqueueChunk(byteArrayOf(0, 1, 0, 1)) // 2 samples of 16-bit PCM
    }

    @Test
    fun `audio format config has correct default values`() {
        val config = StreamingAudioConfig.PCM_16_24KHZ_MONO

        assertEquals(24000, config.sampleRate)
        assertEquals(1, config.channels)
        assertEquals(16, config.bitsPerSample)
    }

    @Test
    fun `stop releases resources and requires re-initialize`() = runBlocking {
        val config = StreamingAudioConfig.PCM_16_24KHZ_MONO
        player.initialize(config)
        player.startPlayback()
        assertTrue(player.isInitialized())

        player.stop()
        assertFalse(player.isInitialized())
    }

    @Test
    fun `re-initialize after stop creates new AudioTrack`() = runBlocking {
        val config = StreamingAudioConfig.PCM_16_24KHZ_MONO

        // First initialization
        player.initialize(config)
        player.startPlayback()
        assertTrue(player.isInitialized())
        player.enqueueChunk(byteArrayOf(0, 1, 0, 1))

        // Stop and re-initialize
        player.stop()
        assertFalse(player.isInitialized())

        // Should be able to re-initialize and use again
        player.initialize(config)
        player.startPlayback()
        assertTrue(player.isInitialized())
        player.enqueueChunk(byteArrayOf(0, 1, 0, 1)) // Should not throw
    }

    @Test
    fun `stop is idempotent - calling multiple times does not throw`() = runBlocking {
        val config = StreamingAudioConfig.PCM_16_24KHZ_MONO
        player.initialize(config)
        player.startPlayback()

        // Multiple stop calls should not throw
        player.stop()
        player.stop()
        player.stop()
        assertFalse(player.isInitialized())
    }

    @Test
    fun `multiple enqueueChunk calls write cumulative data`() = runBlocking {
        val config = StreamingAudioConfig.PCM_16_24KHZ_MONO
        player.initialize(config)
        player.startPlayback()

        // Multiple chunks should not throw
        player.enqueueChunk(byteArrayOf(0, 1, 0, 1))
        player.enqueueChunk(byteArrayOf(2, 3, 2, 3))
        player.enqueueChunk(byteArrayOf(4, 5, 4, 5))
    }

    @Test(expected = IllegalStateException::class)
    fun `startPlayback throws when player not initialized`() {
        player.startPlayback()
    }

    @Test
    fun `sequential lifecycle - initialize play enqueue stop re-initialize play enqueue`() = runBlocking {
        val config = StreamingAudioConfig.PCM_16_24KHZ_MONO

        // First cycle
        player.initialize(config)
        player.startPlayback()
        player.enqueueChunk(byteArrayOf(0, 1, 0, 1))
        player.stop()

        // Second cycle - should work cleanly after stop
        assertFalse(player.isInitialized())
        player.initialize(config)
        player.startPlayback()
        assertTrue(player.isInitialized())
        player.enqueueChunk(byteArrayOf(0, 1, 0, 1))
        player.stop()
        assertFalse(player.isInitialized())
    }

    @Test
    fun `initializing when already initialized re-creates AudioTrack`() = runBlocking {
        val config = StreamingAudioConfig.PCM_16_24KHZ_MONO

        // First initialization
        player.initialize(config)
        player.startPlayback()
        assertTrue(player.isInitialized())

        // Re-initialize without stopping first
        player.initialize(config)
        player.startPlayback()
        assertTrue(player.isInitialized())

        // Should still be usable
        player.enqueueChunk(byteArrayOf(0, 1, 0, 1))
        player.stop()
    }

    @Test
    fun `enqueueChunk after stop without re-initialize throws`() = runBlocking {
        val config = StreamingAudioConfig.PCM_16_24KHZ_MONO
        player.initialize(config)
        player.startPlayback()
        player.enqueueChunk(byteArrayOf(0, 1, 0, 1))
        player.stop()

        // Should throw because player is no longer initialized
        var threw = false
        try {
            player.enqueueChunk(byteArrayOf(0, 1, 0, 1))
        } catch (e: IllegalStateException) {
            threw = true
        }
        assertTrue("Expected IllegalStateException after stop without re-initialize", threw)
    }

    @Test
    fun `stop after stop is safe`() = runBlocking {
        val config = StreamingAudioConfig.PCM_16_24KHZ_MONO
        player.initialize(config)
        player.startPlayback()
        player.stop()

        // Second stop should not throw
        player.stop()
        assertFalse(player.isInitialized())
    }

    @Test
    fun `enqueueChunk with large data does not throw`() = runBlocking {
        val config = StreamingAudioConfig.PCM_16_24KHZ_MONO
        player.initialize(config)
        player.startPlayback()

        // Large chunk (48KB = 1 second of 24kHz 16-bit mono PCM)
        val largeChunk = ByteArray(48000) { (it % 256).toByte() }
        player.enqueueChunk(largeChunk)
    }

    @Test
    fun `multiple small chunks then stop then re-initialize lifecycle`() = runBlocking {
        val config = StreamingAudioConfig.PCM_16_24KHZ_MONO

        player.initialize(config)
        player.startPlayback()

        // Simulate streaming: enqueue many small chunks
        repeat(10) { i ->
            player.enqueueChunk(byteArrayOf(i.toByte(), (i + 1).toByte()))
        }

        player.stop()
        assertFalse(player.isInitialized())

        // Re-initialize for next utterance
        player.initialize(config)
        player.startPlayback()
        assertTrue(player.isInitialized())

        player.enqueueChunk(byteArrayOf(0, 1, 0, 1))
        player.stop()
        assertFalse(player.isInitialized())
    }
}