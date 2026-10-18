package com.browntowndev.enclave.core.data.remote

import com.browntowndev.enclave.core.testing.download.FakeModelDownloadOrchestrator
import com.browntowndev.enclave.domain.model.download.DownloadState
import com.browntowndev.enclave.domain.model.download.DownloadStatus
import com.browntowndev.enclave.domain.port.download.ModelDownloadOrchestratorPort
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModelDownloadManagerTest {
    private lateinit var fakeOrchestrator: FakeModelDownloadOrchestrator
    private lateinit var orchestrator: ModelDownloadOrchestratorPort

    @BeforeEach
    fun setup() {
        fakeOrchestrator = FakeModelDownloadOrchestrator()
        orchestrator = fakeOrchestrator
    }

    @Test
    fun `manager can use orchestrator port`() {
        // Verify we can use the interface (port)
        fakeOrchestrator.downloadStateFlow.value = DownloadState(status = DownloadStatus.IDLE)

        assert(orchestrator.downloadState.value.status == DownloadStatus.IDLE)
    }
}
