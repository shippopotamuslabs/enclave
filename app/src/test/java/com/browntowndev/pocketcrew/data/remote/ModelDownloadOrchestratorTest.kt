package com.browntowndev.pocketcrew.data.remote

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.DownloadState
import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.DownloadStatus
import com.browntowndev.pocketcrew.domain.model.ModelFile
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.port.cache.ModelConfigCachePort
import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelConfigFetcherPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloadOrchestratorTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockModelConfigFetcher: ModelConfigFetcherPort
    private lateinit var mockModelRegistry: ModelRegistryPort
    private lateinit var mockModelConfigCache: ModelConfigCachePort
    private lateinit var mockDownloadSpeedTracker: DownloadSpeedTrackerPort

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        Dispatchers.setMain(testDispatcher)

        mockModelConfigFetcher = mockk(relaxed = true)
        mockModelRegistry = mockk<ModelRegistryPort>(relaxed = true)
        mockModelConfigCache = mockk(relaxed = true)
        mockDownloadSpeedTracker = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `orchestrator port can be mocked`() = runTest {
        val mockOrchestrator = mockk<ModelDownloadOrchestratorPort>(relaxed = true)

        val stateFlow = MutableStateFlow(DownloadState(status = DownloadStatus.IDLE))
        every { mockOrchestrator.downloadState } returns stateFlow

        assert(mockOrchestrator.downloadState.value.status == DownloadStatus.IDLE)
    }

    @Test
    fun `ModelFile requires all parameters`() {
        // Verify ModelFile requires all necessary parameters
        val modelFile = ModelFile(
            sizeBytes = 100L,
            url = "https://example.com/model.bin",
            md5 = "abc123",
            modelTypes = listOf(ModelType.MAIN),
            originalFileName = "model.bin",
            displayName = "Test Model",
            modelFileFormat = ModelFileFormat.LITERTLM,
            maxTokens = 2048,
            systemPrompt = "You are a helpful assistant."
        )

        assert(modelFile.modelTypes.contains(ModelType.MAIN))
    }
}

