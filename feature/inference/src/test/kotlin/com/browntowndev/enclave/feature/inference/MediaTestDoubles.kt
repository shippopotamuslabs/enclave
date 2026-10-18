package com.browntowndev.enclave.feature.inference

import android.content.Context
import com.browntowndev.enclave.domain.model.media.GenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.ImageGenerationSettings
import com.browntowndev.enclave.domain.model.media.ImageGenerationTarget
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationBackendKind
import com.browntowndev.enclave.domain.model.media.LocalImageGenerationModelId
import com.browntowndev.enclave.domain.model.config.OpenRouterModelEndpoint
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.port.media.ImageGenerationPort
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogPort
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogResult
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.feature.inference.fal.FalRequest
import com.browntowndev.enclave.feature.inference.media.FalImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.FalMusicGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.FalVideoGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.GoogleImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.GoogleMusicGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.GoogleVideoGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.OpenAiImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.OpenAiVideoGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.XaiImageGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.XaiVideoGenerationAdapter
import com.browntowndev.enclave.feature.inference.media.local.LocalImageGenerationEngine
import com.browntowndev.enclave.feature.inference.media.local.LocalImageGenerationNativeRequest
import com.browntowndev.enclave.feature.inference.media.local.LocalImageGenerationNativeResult
import com.browntowndev.enclave.feature.inference.media.local.LocalStableDiffusionImageGenerator
import com.browntowndev.enclave.feature.inference.media.local.LocalStableDiffusionGenerationResult
import com.browntowndev.enclave.feature.inference.media.local.MnnCpuStableDiffusionBackend
import com.browntowndev.enclave.feature.inference.media.local.QnnStableDiffusionBackend
import com.browntowndev.enclave.localimagegen.NativeImageGenGenerateParams
import com.browntowndev.enclave.localimagegen.NativeLocalImageGenRuntime
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter

/**
 * Sequenced [FakeFalService]: adds request-body capture, per-call FalRequest and
 * status sequencing, a result-only failure knob, an on-status hook, and
 * resolveMediaBytes recording on top of the shared fake.
 */
class SequencedFalService : FakeFalService() {
    val submittedBodies = mutableListOf<JsonObject>()
    val requestQueue = ArrayDeque<FalRequest>()
    val statusSequence = ArrayDeque<JsonObject>()
    var onStatusPolled: (() -> Unit)? = null
    var resultFailure: Throwable? = null
    val resolvedMediaUrls = mutableListOf<String>()

    override fun submitQueue(
        apiKey: String,
        modelId: String,
        body: JsonObject,
        baseUrl: String?,
    ): FalRequest {
        submittedBodies += body
        submitQueueCalls += modelId
        failure?.let { throw it }
        return if (requestQueue.isNotEmpty()) requestQueue.removeFirst() else submitQueueResult
    }

    override fun status(apiKey: String, statusUrl: String): JsonObject {
        statusCalls += statusUrl
        failure?.let { throw it }
        onStatusPolled?.invoke()
        return when {
            statusSequence.size > 1 -> statusSequence.removeFirst()
            statusSequence.size == 1 -> statusSequence.first()
            else -> statusResult ?: error("SequencedFalService.statusResult not set")
        }
    }

    override fun result(apiKey: String, responseUrl: String): JsonObject {
        resultCalls += responseUrl
        resultFailure?.let { throw it }
        failure?.let { throw it }
        return resultResult ?: error("SequencedFalService.resultResult not set")
    }

    override fun resolveMediaBytes(url: String, apiKey: String?): ByteArray {
        resolvedMediaUrls += url
        return resolveMediaBytesResult
    }
}

/** Knobbed [ApiModelCatalogPort] fake: per-model results, failure knob, call recording. */
class RecordingApiModelCatalogPort : ApiModelCatalogPort {
    data class FetchModelCall(
        val provider: ApiProvider,
        val apiKey: String,
        val modelId: String,
        val baseUrl: String?,
    )

    val fetchModelCalls = mutableListOf<FetchModelCall>()
    var models = mutableMapOf<Pair<String, String?>, DiscoveredApiModel?>()
    var failure: Throwable? = null

    override suspend fun fetchModels(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String?,
    ): ApiModelCatalogResult = ApiModelCatalogResult(emptyList())

    override suspend fun fetchModel(
        provider: ApiProvider,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
    ): DiscoveredApiModel? {
        fetchModelCalls += FetchModelCall(provider, apiKey, modelId, baseUrl)
        failure?.let { throw it }
        return models[modelId to baseUrl]
    }

    override suspend fun fetchModelEndpoints(
        modelId: String,
        apiKey: String,
        baseUrl: String?,
    ): List<OpenRouterModelEndpoint> = emptyList()
}

/** [FakeStudioRepository] wrapper that also records readMediaBytes calls. */
class RecordingStudioRepository(
    val delegate: FakeStudioRepository = FakeStudioRepository(),
) : StudioRepositoryPort by delegate {
    val mediaBytes: MutableMap<String, ByteArray> get() = delegate.mediaBytes
    val readMediaBytesCalls = mutableListOf<String>()

    override suspend fun readMediaBytes(localUri: String): ByteArray? {
        readMediaBytesCalls += localUri
        return delegate.readMediaBytes(localUri)
    }
}

/** Recording [ImageGenerationPort] fake with full call capture. */
class RecordingImageGenerationPort : ImageGenerationPort {
    data class Call(
        val prompt: String,
        val target: ImageGenerationTarget,
        val settings: GenerationSettings,
    )

    val calls = mutableListOf<Call>()
    var result: Result<List<ByteArray>> = Result.success(emptyList())

    override suspend fun generateImage(
        prompt: String,
        target: ImageGenerationTarget,
        settings: GenerationSettings,
        onProgress: (ImageGenerationProgress) -> Unit,
    ): Result<List<ByteArray>> {
        calls += Call(prompt, target, settings)
        return result
    }
}

/** GpuCapabilityPort fake with a settable support flag. */
class KnobbedGpuCapabilityPort(
    var supported: Boolean = true,
) : com.browntowndev.enclave.domain.port.media.GpuCapabilityPort {
    override fun isLocalImageGenerationSupported(): Boolean = supported
}

// region Media adapter subclass fakes (owned adapters as collaborators)

class FakeOpenAiImageGenerationAdapter : OpenAiImageGenerationAdapter(FakeOpenAiClientProviderPort()) {
    data class Call(
        val prompt: String,
        val apiKey: String,
        val modelId: String,
        val baseUrl: String?,
        val settings: GenerationSettings,
        val referenceImage: ByteArray?,
    )

    val calls = mutableListOf<Call>()
    var result: Result<List<ByteArray>> = Result.success(emptyList())

    override suspend fun generateImage(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
        referenceImage: ByteArray?,
    ): Result<List<ByteArray>> {
        calls += Call(prompt, apiKey, modelId, baseUrl, settings, referenceImage)
        return result
    }
}

class FakeGoogleImageGenerationAdapter : GoogleImageGenerationAdapter(FakeGoogleGenAiClientProviderPort()) {
    data class Call(
        val prompt: String,
        val apiKey: String,
        val modelId: String,
        val baseUrl: String?,
        val settings: GenerationSettings,
        val referenceImage: ByteArray?,
    )

    val calls = mutableListOf<Call>()
    var result: Result<List<ByteArray>> = Result.success(emptyList())

    override suspend fun generateImage(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
        referenceImage: ByteArray?,
    ): Result<List<ByteArray>> {
        calls += Call(prompt, apiKey, modelId, baseUrl, settings, referenceImage)
        return result
    }
}

class FakeXaiImageGenerationAdapter : XaiImageGenerationAdapter(FakeOpenAiClientProviderPort(), OkHttpClient()) {
    data class Call(
        val prompt: String,
        val apiKey: String,
        val modelId: String,
        val baseUrl: String?,
        val settings: GenerationSettings,
        val referenceImage: ByteArray?,
    )

    val calls = mutableListOf<Call>()
    var result: Result<List<ByteArray>> = Result.success(emptyList())

    override suspend fun generateImage(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
        referenceImage: ByteArray?,
    ): Result<List<ByteArray>> {
        calls += Call(prompt, apiKey, modelId, baseUrl, settings, referenceImage)
        return result
    }
}

class FakeFalImageGenerationAdapter :
    FalImageGenerationAdapter(FakeFalService(), FakeFalOutputSchemaResolver(), 0L) {
    data class Call(
        val prompt: String,
        val apiKey: String,
        val modelId: String,
        val baseUrl: String?,
        val settings: GenerationSettings,
        val referenceImage: ByteArray?,
        val supportsEdit: Boolean,
    )

    val calls = mutableListOf<Call>()
    var result: Result<List<ByteArray>> = Result.success(emptyList())

    override suspend fun generateImage(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
        referenceImage: ByteArray?,
        supportsEdit: Boolean,
    ): Result<List<ByteArray>> {
        calls += Call(prompt, apiKey, modelId, baseUrl, settings, referenceImage, supportsEdit)
        return result
    }
}

class FakeOpenAiVideoGenerationAdapter : OpenAiVideoGenerationAdapter(OkHttpClient(), 0L) {
    data class Call(
        val prompt: String,
        val apiKey: String,
        val modelId: String,
        val baseUrl: String?,
        val settings: GenerationSettings,
        val referenceImage: ByteArray?,
    )

    val calls = mutableListOf<Call>()
    var result: Result<ByteArray> = Result.success(ByteArray(0))

    override suspend fun generateVideo(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
        referenceImage: ByteArray?,
    ): Result<ByteArray> {
        calls += Call(prompt, apiKey, modelId, baseUrl, settings, referenceImage)
        return result
    }
}

class FakeGoogleVideoGenerationAdapter :
    GoogleVideoGenerationAdapter(OkHttpClient(), FakeGoogleGenAiClientProviderPort(), 0L) {
    data class Call(
        val prompt: String,
        val apiKey: String,
        val modelId: String,
        val baseUrl: String?,
        val settings: GenerationSettings,
    )

    val calls = mutableListOf<Call>()
    var result: Result<ByteArray> = Result.success(ByteArray(0))

    override suspend fun generateVideo(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
    ): Result<ByteArray> {
        calls += Call(prompt, apiKey, modelId, baseUrl, settings)
        return result
    }
}

class FakeXaiVideoGenerationAdapter : XaiVideoGenerationAdapter(OkHttpClient(), 0L) {
    data class Call(
        val prompt: String,
        val apiKey: String,
        val modelId: String,
        val baseUrl: String?,
        val settings: GenerationSettings,
        val referenceImage: ByteArray?,
    )

    val calls = mutableListOf<Call>()
    var result: Result<ByteArray> = Result.success(ByteArray(0))

    override suspend fun generateVideo(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
        referenceImage: ByteArray?,
    ): Result<ByteArray> {
        calls += Call(prompt, apiKey, modelId, baseUrl, settings, referenceImage)
        return result
    }
}

class FakeFalVideoGenerationAdapter :
    FalVideoGenerationAdapter(FakeFalService(), FakeFalOutputSchemaResolver(), 0L) {
    data class Call(
        val prompt: String,
        val apiKey: String,
        val modelId: String,
        val baseUrl: String?,
        val settings: GenerationSettings,
        val referenceImage: ByteArray?,
        val supportsImageToVideo: Boolean,
    )

    val calls = mutableListOf<Call>()
    var result: Result<ByteArray> = Result.success(ByteArray(0))

    override suspend fun generateVideo(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
        referenceImage: ByteArray?,
        supportsImageToVideo: Boolean,
    ): Result<ByteArray> {
        calls += Call(prompt, apiKey, modelId, baseUrl, settings, referenceImage, supportsImageToVideo)
        return result
    }
}

class FakeGoogleMusicGenerationAdapter : GoogleMusicGenerationAdapter(FakeGoogleGenAiClientProviderPort()) {
    data class Call(
        val prompt: String,
        val apiKey: String,
        val modelId: String,
        val baseUrl: String?,
        val settings: GenerationSettings,
    )

    val calls = mutableListOf<Call>()
    var result: Result<ByteArray> = Result.success(ByteArray(0))

    override suspend fun generateMusic(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
    ): Result<ByteArray> {
        calls += Call(prompt, apiKey, modelId, baseUrl, settings)
        return result
    }
}

class FakeFalMusicGenerationAdapter :
    FalMusicGenerationAdapter(FakeFalService(), FakeFalOutputSchemaResolver(), 0L) {
    data class Call(
        val prompt: String,
        val apiKey: String,
        val modelId: String,
        val baseUrl: String?,
        val settings: GenerationSettings,
    )

    val calls = mutableListOf<Call>()
    var result: Result<ByteArray> = Result.success(ByteArray(0))

    override suspend fun generateMusic(
        prompt: String,
        apiKey: String,
        modelId: String,
        baseUrl: String?,
        settings: GenerationSettings,
    ): Result<ByteArray> {
        calls += Call(prompt, apiKey, modelId, baseUrl, settings)
        return result
    }
}

// endregion

// region Local image generation fakes

class FakeQnnStableDiffusionBackend(
    runtime: NativeLocalImageGenRuntime,
) : QnnStableDiffusionBackend(runtime) {
    var cancelCount = 0
    val generateCalls = mutableListOf<LocalImageGenerationNativeRequest>()
    var result: Result<LocalImageGenerationNativeResult> =
        Result.success(LocalImageGenerationNativeResult(emptyList(), 0, 0, 0L))

    override fun cancel() {
        cancelCount++
    }

    override suspend fun generate(
        request: LocalImageGenerationNativeRequest,
    ): Result<LocalImageGenerationNativeResult> {
        generateCalls += request
        return result
    }
}

class FakeMnnCpuStableDiffusionBackend(
    runtime: NativeLocalImageGenRuntime,
) : MnnCpuStableDiffusionBackend(runtime) {
    var cancelCount = 0
    val generateCalls = mutableListOf<LocalImageGenerationNativeRequest>()
    var result: Result<LocalImageGenerationNativeResult> =
        Result.success(LocalImageGenerationNativeResult(emptyList(), 0, 0, 0L))

    override fun cancel() {
        cancelCount++
    }

    override suspend fun generate(
        request: LocalImageGenerationNativeRequest,
    ): Result<LocalImageGenerationNativeResult> {
        generateCalls += request
        return result
    }
}

class FakeLocalImageGenerationEngine(
    runtime: NativeLocalImageGenRuntime,
) : LocalImageGenerationEngine(
        FakeQnnStableDiffusionBackend(runtime),
        FakeMnnCpuStableDiffusionBackend(runtime),
        runtime,
    ) {
    var cancelCount = 0
    val generateCalls = mutableListOf<LocalImageGenerationNativeRequest>()
    val resultQueue = ArrayDeque<Result<LocalImageGenerationNativeResult>>()
    var result: Result<LocalImageGenerationNativeResult> =
        Result.success(LocalImageGenerationNativeResult(emptyList(), 512, 512, 0L))

    override fun cancel() {
        cancelCount++
    }

    override suspend fun generate(
        request: LocalImageGenerationNativeRequest,
    ): Result<LocalImageGenerationNativeResult> {
        generateCalls += request
        return if (resultQueue.isNotEmpty()) resultQueue.removeFirst() else result
    }
}

class FakeLocalStableDiffusionImageGenerator(
    context: Context,
    runtime: NativeLocalImageGenRuntime,
) : LocalStableDiffusionImageGenerator(
        context = context,
        engine = FakeLocalImageGenerationEngine(runtime),
        nativeRuntime = runtime,
        inferenceLockManager = FakeInferenceLockManager(),
        studioRepository = FakeStudioRepository(),
        dispatcher = Dispatchers.Unconfined,
    ) {
    data class Call(
        val prompt: String,
        val target: ImageGenerationTarget.LocalStableDiffusion,
        val settings: ImageGenerationSettings,
    )

    val calls = mutableListOf<Call>()
    val resultQueue = ArrayDeque<Result<LocalStableDiffusionGenerationResult>>()
    var result: Result<LocalStableDiffusionGenerationResult> =
        Result.success(
            LocalStableDiffusionGenerationResult(
                outputFileReferences = emptyList(),
                width = 512,
                height = 512,
                backend = LocalImageGenerationBackendKind.QNN_NPU,
            ),
        )

    override suspend fun generate(
        prompt: String,
        target: ImageGenerationTarget.LocalStableDiffusion,
        settings: ImageGenerationSettings,
        onProgress: (ImageGenerationProgress) -> Unit,
    ): Result<LocalStableDiffusionGenerationResult> {
        calls += Call(prompt, target, settings)
        return if (resultQueue.isNotEmpty()) resultQueue.removeFirst() else result
    }
}

// endregion

/**
 * Robolectric shadow for [NativeLocalImageGenRuntime]. The real class loads the
 * JNI library from its constructor, so it can never be instantiated (or
 * subclass-faked) on the JVM; the shadow replaces the constructor and every
 * native-backed method with knob-driven behavior. Configure via the companion.
 */
@Implements(NativeLocalImageGenRuntime::class)
class ShadowFakeNativeLocalImageGenRuntime {
    @Implementation
    fun __constructor__(context: Context) {
        // Skip nativeInit / component-callback registration.
    }

    @Implementation
    fun probe(backend: LocalImageGenerationBackendKind): Int {
        probeCalls += backend
        return probeResults[backend] ?: defaultProbeResult
    }

    @Implementation
    fun generate(params: NativeImageGenGenerateParams): Int {
        generateCalls += params
        return generateHandler?.invoke(params) ?: generateResult
    }

    @Implementation
    fun lastGenerationError(): String? = lastError

    @Implementation
    fun currentGenerationProgress(): Pair<Int, Int>? = progress

    @Implementation
    fun cancel() {
        cancelCount++
    }

    @Implementation
    fun ensureQnnRuntimeForModel(modelId: LocalImageGenerationModelId) {
        ensuredModels += modelId
    }

    @Implementation
    fun isQnnRuntimeReadyForModel(modelId: LocalImageGenerationModelId): Boolean = qnnRuntimeReady

    companion object {
        var defaultProbeResult = 0
        val probeResults = mutableMapOf<LocalImageGenerationBackendKind, Int>()
        val probeCalls = mutableListOf<LocalImageGenerationBackendKind>()
        var generateResult = 0
        var generateHandler: ((NativeImageGenGenerateParams) -> Int)? = null
        val generateCalls = mutableListOf<NativeImageGenGenerateParams>()
        var lastError: String? = null
        var progress: Pair<Int, Int>? = null
        var cancelCount = 0
        var qnnRuntimeReady = true
        val ensuredModels = mutableListOf<LocalImageGenerationModelId>()

        @Resetter
        @JvmStatic
        fun reset() {
            defaultProbeResult = 0
            probeResults.clear()
            probeCalls.clear()
            generateResult = 0
            generateHandler = null
            generateCalls.clear()
            lastError = null
            progress = null
            cancelCount = 0
            qnnRuntimeReady = true
            ensuredModels.clear()
        }
    }
}
