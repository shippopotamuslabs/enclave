package com.browntowndev.enclave.core.data.repository

import android.content.SharedPreferences
import androidx.sqlite.db.SupportSQLiteQuery
import com.browntowndev.enclave.core.data.local.EmbeddingDao
import com.browntowndev.enclave.core.data.local.EmbeddingEntity
import com.browntowndev.enclave.core.data.local.MediaProviderDao
import com.browntowndev.enclave.core.data.local.MediaProviderEntity
import com.browntowndev.enclave.core.data.local.MemoriesDao
import com.browntowndev.enclave.core.data.local.MemoriesEntity
import com.browntowndev.enclave.core.data.local.MemoryEmbeddingMatch
import com.browntowndev.enclave.core.data.local.StudioAlbumDao
import com.browntowndev.enclave.core.data.local.StudioAlbumEntity
import com.browntowndev.enclave.core.data.local.StudioMediaDao
import com.browntowndev.enclave.core.data.local.StudioMediaEntity
import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.local.TtsProviderEntity
import com.browntowndev.enclave.core.data.security.ApiKeyManager
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeGpuCapability
import com.browntowndev.enclave.core.testing.FakeImageGenerationPort
import com.browntowndev.enclave.core.testing.FakeLocalImageGenerationCapability
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeMediaProviderRepository
import com.browntowndev.enclave.core.testing.FakeMusicGenerationPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeUtilityModelFile
import com.browntowndev.enclave.core.testing.FakeVideoGenerationPort
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.model.media.GeneratedMediaAsset
import com.browntowndev.enclave.domain.model.media.ImageGenerationProgress
import com.browntowndev.enclave.domain.model.media.MediaGenerationRequest
import com.browntowndev.enclave.domain.usecase.media.GenerateImageUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateMediaUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateMusicUseCase
import com.browntowndev.enclave.domain.usecase.media.GenerateVideoUseCase
import com.browntowndev.enclave.domain.usecase.media.LocalStableDiffusionTargetResolver
import com.browntowndev.enclave.domain.usecase.media.ResolveAssignedImageGenerationTargetUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveAssignedMediaProviderUseCase
import com.browntowndev.enclave.domain.usecase.media.ResolveMediaModelTypeUseCase
import com.browntowndev.enclave.domain.usecase.media.SelectLocalImageGenerationBackendUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import com.browntowndev.enclave.core.testing.TestClock

/**
 * Hand-written fakes for the repository test suite, batch REPO-B (ENC-28):
 * in-memory recording DAO fakes, an in-memory [SharedPreferences], subclass
 * fakes for the open search repositories / media use case, and a stub
 * OkHttp [Interceptor] that serves canned responses for the real
 * [OkHttpClient] used by the HTTP-backed repositories.
 */

/**
 * Inert real [ApiKeyManager] over a [FakeSharedPreferences]: used only as a
 * super-constructor argument for the subclass search-repository fakes
 * below, whose overridden `search` never touches it. Tests that actually
 * exercise an ApiKeyManager use the Robolectric-backed
 * `testApiKeyManager` from RepositoryTestDoubles instead.
 */
fun fakeApiKeyManager(prefs: FakeSharedPreferences = FakeSharedPreferences()): ApiKeyManager =
    ApiKeyManager(
        { prefs },
        InMemoryPreferencesDataStore(),
        CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

/** In-memory [SharedPreferences] implementation with real map semantics. */
class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(
        key: String,
        defValues: Set<String>?,
    ): Set<String>? = (values[key] as? Set<String>) ?: defValues

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = values[key] as? Int ?: defValue

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = values[key] as? Long ?: defValue

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float = values[key] as? Float ?: defValue

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners -= listener
    }

    inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(
            key: String,
            value: String?,
        ): SharedPreferences.Editor = apply { pending[key] = value }

        override fun putStringSet(
            key: String,
            values: Set<String>?,
        ): SharedPreferences.Editor = apply { pending[key] = values }

        override fun putInt(
            key: String,
            value: Int,
        ): SharedPreferences.Editor = apply { pending[key] = value }

        override fun putLong(
            key: String,
            value: Long,
        ): SharedPreferences.Editor = apply { pending[key] = value }

        override fun putFloat(
            key: String,
            value: Float,
        ): SharedPreferences.Editor = apply { pending[key] = value }

        override fun putBoolean(
            key: String,
            value: Boolean,
        ): SharedPreferences.Editor = apply { pending[key] = value }

        override fun remove(key: String): SharedPreferences.Editor =
            apply {
                pending.remove(key)
                removals += key
            }

        override fun clear(): SharedPreferences.Editor = apply { clearAll = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearAll) values.clear()
            removals.forEach { values.remove(it) }
            values.putAll(pending)
        }
    }
}

/** Recording in-memory [MemoriesDao] fake: stores entities by id and records every mutating call. */
class FakeMemoriesDao : MemoriesDao {
    val entities = linkedMapOf<String, MemoriesEntity>()
    val insertCalls = mutableListOf<MemoriesEntity>()
    val updateCalls = mutableListOf<MemoriesEntity>()
    val softDeleteCalls = mutableListOf<Pair<String, Long>>()
    val getMemoriesByCategoriesCalls = mutableListOf<List<MemoryCategory>>()

    /** Entities emitted by [getAllMemoriesFlow]. */
    var flowEntities: List<MemoriesEntity> = emptyList()

    /** Number of [getAllMemoriesFlow] invocations. */
    var getAllMemoriesFlowCalls = 0
        private set

    override suspend fun insertMemory(memory: MemoriesEntity) {
        insertCalls += memory
        entities[memory.id] = memory
    }

    override suspend fun updateMemory(memory: MemoriesEntity) {
        updateCalls += memory
        entities[memory.id] = memory
    }

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) {
        softDeleteCalls += id to timestamp
        entities[id]?.let { entities[id] = it.copy(deleted = true) }
    }

    override suspend fun purgeDeleted(ids: List<String>) {
        ids.forEach { entities.remove(it) }
    }

    override suspend fun getMemoryById(id: String): MemoriesEntity? = entities[id]

    override suspend fun getMemoriesByCategories(categories: List<MemoryCategory>): List<MemoriesEntity> {
        getMemoriesByCategoriesCalls += categories
        return entities.values.filter { it.category in categories }
    }

    override fun getAllMemoriesFlow(): Flow<List<MemoriesEntity>> {
        getAllMemoriesFlowCalls++
        return flowOf(flowEntities)
    }

    override suspend fun searchMemoriesRaw(query: SupportSQLiteQuery): List<MemoriesEntity> = emptyList()

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<MemoriesEntity> = emptyList()

    override suspend fun markSynced(ids: List<String>) = Unit

    override suspend fun getAllIds(): List<String> = entities.keys.toList()
}

/** Recording [EmbeddingDao] fake: captures the SQL of every raw query instead of executing it. */
class FakeEmbeddingDao : EmbeddingDao {
    /** SQL of every query passed to [insertEmbedding], in call order. */
    val insertEmbeddingQueries = mutableListOf<String>()

    /** SQL of every query passed to [searchMemoryEmbeddingMatches], in call order. */
    val searchMatchesQueries = mutableListOf<String>()

    /** Matches returned by [searchMemoryEmbeddingMatches]. */
    var matches: List<MemoryEmbeddingMatch> = emptyList()

    override suspend fun searchSimilarMessages(query: SupportSQLiteQuery): List<String> = emptyList()

    override suspend fun searchMemoryEmbeddingMatches(query: SupportSQLiteQuery): List<MemoryEmbeddingMatch> {
        searchMatchesQueries += query.sql
        return matches
    }

    override suspend fun insertEmbedding(query: SupportSQLiteQuery): Long {
        insertEmbeddingQueries += query.sql
        return 1L
    }

    override suspend fun getEmbeddingMetadata(messageId: String): EmbeddingEntity? = null

    override suspend fun insertEmbeddingMetadata(entity: EmbeddingEntity) = Unit

    override suspend fun deleteEmbeddingMetadata(messageId: String) = Unit
}

/** Recording in-memory [StudioMediaDao] fake. [callLog] may be shared with [FakeStudioAlbumDao] to assert call order. */
class FakeStudioMediaDao(
    private val callLog: MutableList<String> = mutableListOf(),
) : StudioMediaDao {
    val entities = linkedMapOf<String, StudioMediaEntity>()
    val insertCalls = mutableListOf<StudioMediaEntity>()
    val softDeleteCalls = mutableListOf<Pair<String, Long>>()
    val updateMediaAlbumCalls = mutableListOf<Triple<List<String>, String?, Long>>()
    val reassignCalls = mutableListOf<Pair<String, Long>>()

    /** When non-null, returned by [getMediaById] for any id (mirrors an `any()` stub). */
    var getMediaByIdOverride: StudioMediaEntity? = null

    /** Rows returned by [getUploaded]. */
    var uploadedEntities: List<StudioMediaEntity> = emptyList()

    override fun getAllMedia(): Flow<List<StudioMediaEntity>> = flowOf(entities.values.toList())

    override suspend fun insertMedia(media: StudioMediaEntity) {
        callLog += "insertMedia"
        insertCalls += media
        entities[media.id] = media
    }

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) {
        callLog += "softDeleteById"
        softDeleteCalls += id to timestamp
    }

    override suspend fun purgeDeleted(ids: List<String>) {
        ids.forEach { entities.remove(it) }
    }

    override suspend fun getMediaById(id: String): StudioMediaEntity? = getMediaByIdOverride ?: entities[id]

    override suspend fun updateMediaAlbum(
        mediaIds: List<String>,
        albumId: String?,
        timestamp: Long,
    ) {
        callLog += "updateMediaAlbum"
        updateMediaAlbumCalls += Triple(mediaIds, albumId, timestamp)
    }

    override suspend fun reassignMediaToDefault(
        albumId: String,
        timestamp: Long,
    ) {
        callLog += "reassignMediaToDefault"
        reassignCalls += albumId to timestamp
    }

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<StudioMediaEntity> = emptyList()

    override suspend fun markSynced(ids: List<String>) = Unit

    override suspend fun getPendingUploads(): List<StudioMediaEntity> = emptyList()

    override suspend fun getUploaded(): List<StudioMediaEntity> = uploadedEntities

    override suspend fun getPendingDeletions(): List<StudioMediaEntity> = emptyList()

    override suspend fun markCloudDeleted(id: String) = Unit

    override suspend fun updateStagingUrl(
        id: String,
        url: String?,
    ) = Unit

    override suspend fun markUploaded(
        id: String,
        remoteUrl: String,
        updatedAt: Long,
    ) = Unit

    override suspend fun markUploadFailed(
        id: String,
        updatedAt: Long,
    ) = Unit
}

/** Recording in-memory [StudioAlbumDao] fake. [callLog] may be shared with [FakeStudioMediaDao] to assert call order. */
class FakeStudioAlbumDao(
    private val callLog: MutableList<String> = mutableListOf(),
) : StudioAlbumDao {
    val entities = linkedMapOf<String, StudioAlbumEntity>()
    val insertCalls = mutableListOf<StudioAlbumEntity>()
    val softDeleteCalls = mutableListOf<Pair<String, Long>>()

    override fun observeAllAlbums(): Flow<List<StudioAlbumEntity>> = flowOf(entities.values.toList())

    override suspend fun insertAlbum(album: StudioAlbumEntity) {
        callLog += "insertAlbum"
        insertCalls += album
        entities[album.id] = album
    }

    override suspend fun updateAlbumName(
        id: String,
        name: String,
        timestamp: Long,
    ) = Unit

    override suspend fun getAlbumById(id: String): StudioAlbumEntity? = entities[id]

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) {
        callLog += "albumSoftDeleteById"
        softDeleteCalls += id to timestamp
    }

    override suspend fun purgeDeleted(ids: List<String>) {
        ids.forEach { entities.remove(it) }
    }

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<StudioAlbumEntity> = emptyList()

    override suspend fun markSynced(ids: List<String>) = Unit
}

/** Recording in-memory [MediaProviderDao] fake. */
class FakeMediaProviderDao : MediaProviderDao {
    val entities = linkedMapOf<String, MediaProviderEntity>()
    val upsertCalls = mutableListOf<MediaProviderEntity>()
    val softDeleteCalls = mutableListOf<Pair<String, Long>>()

    /** Entities emitted by [observeAllMediaProviders]. */
    var observedEntities: List<MediaProviderEntity> = emptyList()

    override suspend fun getMediaProvider(id: String): MediaProviderEntity? = entities[id]

    override fun observeAllMediaProviders(): Flow<List<MediaProviderEntity>> = flowOf(observedEntities)

    override suspend fun upsert(entity: MediaProviderEntity) {
        upsertCalls += entity
        entities[entity.id] = entity
    }

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) {
        softDeleteCalls += id to timestamp
    }

    override suspend fun purgeDeleted(ids: List<String>) {
        ids.forEach { entities.remove(it) }
    }

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<MediaProviderEntity> = emptyList()

    override suspend fun markSynced(ids: List<String>) = Unit
}

/** Recording in-memory [TtsProviderDao] fake. */
class FakeTtsProviderDao : TtsProviderDao {
    val entities = linkedMapOf<String, TtsProviderEntity>()
    val upsertCalls = mutableListOf<TtsProviderEntity>()
    val softDeleteCalls = mutableListOf<Pair<String, Long>>()

    /** Entities emitted by [getTtsProviders]. */
    var flowEntities: List<TtsProviderEntity> = emptyList()

    override fun getTtsProviders(): Flow<List<TtsProviderEntity>> = flowOf(flowEntities)

    override suspend fun getTtsProvidersSync(): List<TtsProviderEntity> = entities.values.toList()

    override suspend fun getTtsProvider(id: String): TtsProviderEntity? = entities[id]

    override suspend fun upsertTtsProvider(entity: TtsProviderEntity) {
        upsertCalls += entity
        entities[entity.id] = entity
    }

    override suspend fun softDeleteById(
        id: String,
        timestamp: Long,
    ) {
        softDeleteCalls += id to timestamp
    }

    override suspend fun purgeDeleted(ids: List<String>) {
        ids.forEach { entities.remove(it) }
    }

    override suspend fun claimPending() = Unit

    override suspend fun getClaimedForSync(): List<TtsProviderEntity> = emptyList()

    override suspend fun markSynced(ids: List<String>) = Unit
}

/** Subclass fake for [TavilySearchRepository]: returns canned results or throws [searchFailure]. */
class FakeTavilySearchRepository : TavilySearchRepository(OkHttpClient(), fakeApiKeyManager()) {
    val searchQueries = mutableListOf<String>()
    var resultsByQuery: Map<String, String> = emptyMap()
    var defaultResult: String = """{"results":[]}"""
    var searchFailure: Throwable? = null

    override fun search(query: String): String {
        searchQueries += query
        searchFailure?.let { throw it }
        return resultsByQuery[query] ?: defaultResult
    }
}

/** Subclass fake for [ExaSearchRepository]: returns canned results or throws [searchFailure]. */
class FakeExaSearchRepository : ExaSearchRepository(OkHttpClient(), fakeApiKeyManager()) {
    val searchQueries = mutableListOf<String>()
    var resultsByQuery: Map<String, String> = emptyMap()
    var defaultResult: String = """{"results":[]}"""
    var searchFailure: Throwable? = null

    override fun search(query: String): String {
        searchQueries += query
        searchFailure?.let { throw it }
        return resultsByQuery[query] ?: defaultResult
    }
}

/** Subclass fake for [XSearchRepository]: returns canned results or throws [searchFailure]. */
class FakeXSearchRepository :
    XSearchRepository(
        OkHttpClient(),
        fakeApiKeyManager(),
        XUserTimelineFetcher(OkHttpClient(), fakeApiKeyManager()),
    ) {
    val searchQueries = mutableListOf<String>()
    var resultsByQuery: Map<String, String> = emptyMap()
    var defaultResult: String = """{"posts":[]}"""
    var searchFailure: Throwable? = null

    override fun search(
        query: String,
        sortOrder: String,
    ): String {
        searchQueries += query
        searchFailure?.let { throw it }
        return resultsByQuery[query] ?: defaultResult
    }
}

/**
 * Subclass fake for the open [GenerateMediaUseCase]: the real sub-use-case
 * graph is wired over `:core:testing` fakes but never invoked, because
 * [invoke] is overridden to return [result] (or throw [failure]).
 */
class FakeGenerateMediaUseCase : GenerateMediaUseCase(
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
        ResolveAssignedMediaProviderUseCase(
            FakeDefaultModelRepository(),
            FakeMediaProviderRepository(),
        ),
        FakeVideoGenerationPort(),
    ),
    GenerateMusicUseCase(
        FakeDefaultModelRepository(),
        FakeMediaProviderRepository(),
        FakeMusicGenerationPort(),
    ),
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
    val requests = mutableListOf<MediaGenerationRequest>()
    var result: Result<List<GeneratedMediaAsset>> = Result.success(emptyList())
    var failure: Throwable? = null

    override suspend fun invoke(
        request: MediaGenerationRequest,
        onImageProgress: (ImageGenerationProgress) -> Unit,
    ): Result<List<GeneratedMediaAsset>> {
        requests += request
        failure?.let { throw it }
        return result
    }
}

/**
 * OkHttp [Interceptor] that records every request and serves the first
 * matching registered route, so a real [OkHttpClient] can back the
 * HTTP repositories in tests without any network or mocking.
 */
class StubHttpInterceptor : Interceptor {
    /** Every request seen, in order. */
    val requests = mutableListOf<Request>()

    private val routes = mutableListOf<Pair<(Request) -> Boolean, (Request) -> Response>>()

    /** Serve [body] with HTTP [code] for requests matching [predicate]. First matching route wins. */
    fun respondWhen(
        predicate: (Request) -> Boolean,
        code: Int,
        body: String,
    ) {
        routes += predicate to { request -> buildResponse(request, code, body) }
    }

    /** Serve [body] with HTTP 200 for every request. */
    fun respondToAll(
        code: Int,
        body: String,
    ) {
        respondWhen({ true }, code, body)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        requests += request
        val handler =
            routes.firstOrNull { (predicate, _) -> predicate(request) }?.second
                ?: throw IllegalStateException("StubHttpInterceptor: no route registered for ${request.url}")
        return handler(request)
    }

    private fun buildResponse(
        request: Request,
        code: Int,
        body: String,
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody())
            .build()
}

/** Real [OkHttpClient] whose only interceptor is a [StubHttpInterceptor]. */
fun stubHttpClient(interceptor: StubHttpInterceptor): OkHttpClient =
    OkHttpClient
        .Builder()
        .addInterceptor(interceptor)
        .build()
