package com.browntowndev.enclave.feature.inference.media

import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import android.util.Log
import com.browntowndev.enclave.domain.port.inference.ImageModerationPort
import com.browntowndev.enclave.domain.port.media.ImageGenerationPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps a delegate [ImageGenerationPort] (the real remote/local fan-out) and runs each generated
 * image through [ImageModerationPort] before returning it. This is the single centralization seam
 * that ensures every image — remote API or on-device diffusion — is moderated before reaching the
 * caller (Studio service or chat tool executor).
 *
 * Flagged images are replaced in place by the bundled placeholder bytes so a partial batch still
 * surfaces the block to the user without failing the whole request.
 *
 * The wrapped [delegate] is supplied via the [DelegateImageGenerationPort] qualifier to avoid a DI
 * cycle between this decorator (the unqualified [ImageGenerationPort]) and the real implementation.
 */
@Singleton
class ModeratingImageGenerationPort
    @Inject
    constructor(
        @param:DelegateImageGenerationPort private val delegate: ImageGenerationPort,
        private val imageModerationPort: ImageModerationPort,
        private val placeholderProvider: BlockedImagePlaceholderProvider,
    ) : ImageGenerationPort {
        override suspend fun generateImage(
            prompt: String,
            target: ImageGenerationTarget,
            settings: GenerationSettings,
            onProgress: (ImageGenerationProgress) -> Unit,
        ): Result<List<ByteArray>> =
            delegate
                .generateImage(prompt, target, settings, onProgress)
                .map { images -> images.map { moderate(it) } }

        private suspend fun moderate(imageBytes: ByteArray): ByteArray {
            val result = imageModerationPort.classify(imageBytes)
            if (result.blocked) {
                Log.i(
                    TAG,
                    "image blocked (${imageBytes.size} bytes); substituting placeholder" +
                        if (result.blockingLabel != null) " (label=${result.blockingLabel})" else "",
                )
                return placeholderProvider.bytes()
            }
            Log.i(TAG, "image passed moderation (${imageBytes.size} bytes)")
            return imageBytes
        }

        private companion object {
            private const val TAG = "ModeratingImageGen"
        }
    }
