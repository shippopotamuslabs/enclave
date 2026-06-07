package com.browntowndev.enclave.feature.inference.media

object RealtimeAudioCaptureGain {
    const val DEFAULT_MIC_GAIN = 1.75f
    private const val PCM16_SAMPLE_MAX = 32_767
    private const val PCM16_UNSIGNED_RANGE = 65_536
    private const val PCM16_SIGNED_MIN = -32_768
    private const val PCM16_SIGNED_MAX = 32_767
    private const val BYTE_MASK = 0xFF
    private const val UNITY_GAIN = 1f
    private const val MIN_FRAME_BYTES = 2
    private const val BYTES_PER_SAMPLE = 2

    fun amplifyPcm16Le(
        frame: ByteArray,
        gain: Float = DEFAULT_MIC_GAIN,
    ): ByteArray {
        if (gain == UNITY_GAIN || frame.size < MIN_FRAME_BYTES) return frame
        val amplified = frame.copyOf()
        var index = 0
        while (index < amplified.size - 1) {
            val low = amplified[index].toInt() and BYTE_MASK
            val high = amplified[index + 1].toInt() and BYTE_MASK
            val sample = (high shl 8) or low
            val signed = if (sample > PCM16_SAMPLE_MAX) sample - PCM16_UNSIGNED_RANGE else sample
            val scaled = (signed * gain).toInt().coerceIn(PCM16_SIGNED_MIN, PCM16_SIGNED_MAX)
            val unsigned = if (scaled < 0) scaled + PCM16_UNSIGNED_RANGE else scaled
            amplified[index] = (unsigned and BYTE_MASK).toByte()
            amplified[index + 1] = ((unsigned shr 8) and BYTE_MASK).toByte()
            index += BYTES_PER_SAMPLE
        }
        return amplified
    }
}
