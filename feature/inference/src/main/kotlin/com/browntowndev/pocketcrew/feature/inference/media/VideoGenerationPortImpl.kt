package com.browntowndev.pocketcrew.feature.inference.media

import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.media.GenerationSettings
import com.browntowndev.pocketcrew.domain.port.media.VideoGenerationPort
import javax.inject.Inject

class VideoGenerationPortImpl @Inject constructor() : VideoGenerationPort {
    override suspend fun generateVideo(
        prompt: String,
        provider: MediaProviderAsset,
        settings: GenerationSettings
    ): Result<ByteArray> {
        return Result.failure(UnsupportedOperationException("Video generation not yet implemented"))
    }
}
