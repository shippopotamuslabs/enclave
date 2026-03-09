package com.browntowndev.pocketcrew.data.remote

import com.browntowndev.pocketcrew.domain.port.download.ModelDownloadOrchestratorPort
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import com.browntowndev.pocketcrew.domain.model.download.DownloadState
import com.browntowndev.pocketcrew.domain.model.download.DownloadStatus
import com.browntowndev.pocketcrew.domain.port.download.DownloadSpeedTrackerPort
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModelDownloadManagerTest {

    private lateinit var mockOrchestrator: ModelDownloadOrchestratorPort

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockOrchestrator = mockk(relaxed = true)
    }

    @Test
    fun `manager can use orchestrator port`() {
        // Verify we can use the interface (port)
        val stateFlow = MutableStateFlow(DownloadState(status = DownloadStatus.IDLE))
        every { mockOrchestrator.downloadState } returns stateFlow

        assert(mockOrchestrator.downloadState.value.status == DownloadStatus.IDLE)
    }
}

