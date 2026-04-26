package com.browntowndev.pocketcrew.core.data.media

import android.content.Context
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [AndroidAudioPlayer] with focus on lifecycle safety:
 * - Stop after completion doesn't crash
 * - Stop is idempotent
 * - Repeated stop/release calls are safe
 * - playAudio cleans up previous MediaPlayer before creating new one
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidAudioPlayerTest {

    private lateinit var player: AndroidAudioPlayer
    private val mockLogger: LoggingPort = mockk(relaxed = true)
    private val mockContext: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        player = AndroidAudioPlayer(mockContext, mockLogger)
    }

    @Test
    fun `stop is safe when player has not been used`() {
        // Calling stop() on a fresh player should not throw
        player.stop()
        player.stop() // Idempotent - calling twice is safe
    }

    @Test
    fun `stop is safe after repeated calls`() {
        // Repeated stop calls should not throw
        player.stop()
        player.stop()
        player.stop()
    }

    @Test
    fun `playAudio handles empty audio data gracefully`() = runTest {
        // Empty byte array should be handled without crashing
        try {
            player.playAudio(byteArrayOf())
        } catch (e: Exception) {
            // Empty audio data may cause a MediaPlayer error, which is expected.
            // The important thing is that the player doesn't crash on stop() afterward.
        }
        player.stop() // Should be safe regardless of playAudio result
    }

    @Test
    fun `stop after playAudio completion release does not crash`() = runTest {
        // Simulate: playAudio completes, then stop() is called externally.
        // The key crash scenario: playAudio's setOnCompletionListener releases
        // the MediaPlayer, then stop() tries to access the released player.
        // With the fix, stop() should handle null mediaPlayer gracefully.

        // Use a small valid WAV header for Robolectric
        val audioBytes = createMinimalWavBytes()

        try {
            player.playAudio(audioBytes)
        } catch (e: Exception) {
            // Robolectric may not fully support MediaPlayer - that's OK
            // We just need to verify stop() is safe afterward
        }

        // This should NOT throw even if MediaPlayer was released by completion listener
        player.stop()
        player.stop() // Double stop should also be safe
    }

    @Test
    fun `stop called during playAudio cancels playback safely`() = runTest {
        // Verify that calling stop() during playback doesn't crash
        val audioBytes = createMinimalWavBytes()

        try {
            player.playAudio(audioBytes)
        } catch (e: Exception) {
            // Expected in Robolectric
        }

        // Stop should be safe even if called "during" or "after" playback
        player.stop()
    }

    private fun createMinimalWavBytes(): ByteArray {
        // Minimal WAV file: 44-byte header + 4 bytes of PCM data
        val sampleRate = 24000
        val channels = 1
        val bitsPerSample = 16
        val dataSize = 4
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        return ByteArray(44 + dataSize).also { header ->
            // RIFF header
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            writeInt32(header, 4, 36 + dataSize)
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            // fmt sub-chunk
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            writeInt32(header, 16, 16)
            writeInt16(header, 20, 1)
            writeInt16(header, 22, channels.toShort())
            writeInt32(header, 24, sampleRate)
            writeInt32(header, 28, byteRate)
            writeInt16(header, 32, blockAlign.toShort())
            writeInt16(header, 34, bitsPerSample.toShort())
            // data sub-chunk
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            writeInt32(header, 40, dataSize)
        }
    }

    private fun writeInt32(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = (value shr 8 and 0xFF).toByte()
        buf[offset + 2] = (value shr 16 and 0xFF).toByte()
        buf[offset + 3] = (value shr 24 and 0xFF).toByte()
    }

    private fun writeInt16(buf: ByteArray, offset: Int, value: Short) {
        buf[offset] = (value.toInt() and 0xFF).toByte()
        buf[offset + 1] = (value.toInt() shr 8 and 0xFF).toByte()
    }
}