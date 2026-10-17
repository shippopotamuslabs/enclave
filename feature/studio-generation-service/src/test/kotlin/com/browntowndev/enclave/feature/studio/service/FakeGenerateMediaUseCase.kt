package com.browntowndev.enclave.feature.studio.service

import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeGpuCapability
import com.browntowndev.enclave.core.testing.FakeImageGenerationPort
import com.browntowndev.enclave.core.testing.FakeLocalImageGenerationCapability
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeUtilityModelFile
import com.browntowndev.enclave.core.testing.FakeVideoGenerationPort
import com.browntowndev.enclave.core.testing.TestClock
import com.browntowndev.enclave.domain.model.media.GeneratedMediaAsset
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.MediaGenerationRequest
import com.browntowndev.enclave.domain.usecase.media.GenerateImageUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateMediaUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateVideoUseCase
import com.browntowndev.enclave.domain.usecase.media.LocalStableDiffusionTargetResolver
import com.browntowndev.enclave.domain.usecase.media.ResolveAssignedImageGenerationTargetUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveAssignedMediaProviderUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveMediaModelTypeUseCase
import com.browntowndev.enclave.domain.usecase.media.SelectLocalImageGenerationBackendUseCase
import com.browntowndev.enclave.core.testing.FakeMusicGenerationPort
import com.browntowndev.enclave.domain.usecase.media.GenerateMusicUseCase
import kotlinx.coroutines.delay

/**
 * Recording [GenerateMediaUseCase] fake (ENC-28): subclass over a real dummy
 * collaborator graph; invoke is fully test-controlled.
 */
class FakeGenerateMediaUseCase :
    GenerateMediaUseCase(
        GenerateImageUseCase(
            ResolveMediaModelTypeUseCase(),
            ResolveAssignedImageGenerationTargetUseCase(
                FakeDefaultModelRepository(),
                FakeMediaProviderRepository(),
                LocalStableDiffusionTargetResolver(
                    FakeLocalModelRepository(),
                    FakeUtilityModelFile(),
                    FakeGpuCapability(),
                    FakeLocalImageGenerationCapability(),
                    SelectLocalImageGenerationBackendUseCase(),
                ),
            ),
            FakeImageGenerationPort(),
        ),
        GenerateVideoUseCase(
            ResolveMediaModelTypeUseCase(),
            ResolveAssignedMediaProviderUseCase(FakeDefaultModelRepository(), FakeMediaProviderRepository()),
            FakeVideoGenerationPort(),
        ),
        GenerateMusicUseCase(FakeDefaultModelRepository(), FakeMediaProviderRepository(), FakeMusicGenerationPort()),
        ResolveMediaModelTypeUseCase(),
        ResolveAssignedImageGenerationTargetUseCase(
            FakeDefaultModelRepository(),
            FakeMediaProviderRepository(),
            LocalStableDiffusionTargetResolver(
                FakeLocalModelRepository(),
                FakeUtilityModelFile(),
                FakeGpuCapability(),
                FakeLocalImageGenerationCapability(),
                SelectLocalImageGenerationBackendUseCase(),
            ),
        ),
        FakeStudioRepository(),
        TestClock(),
    ) {
    var result: Result<List<GeneratedMediaAsset>> = Result.success(emptyList())
    var delayMs: Long = 0

    /** When set, invoke reports this progress before returning. */
    var progressToEmit: ImageGenerationProgress? = null

    val calls = mutableListOf<MediaGenerationRequest>()

    override suspend fun invoke(
        request: MediaGenerationRequest,
        onImageProgress: (ImageGenerationProgress) -> Unit,
    ): Result<List<GeneratedMediaAsset>> {
        calls += request
        if (delayMs > 0) delay(delayMs)
        progressToEmit?.let(onImageProgress)
        return result
    }
}
