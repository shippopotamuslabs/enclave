package com.browntowndev.pocketcrew.domain.port.media

import kotlinx.coroutines.flow.Flow

interface AudioCapturePort {
    fun audioChunks(): Flow<FloatArray>
}
