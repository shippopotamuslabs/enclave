package com.browntowndev.pocketcrew.domain.usecase.download

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.DeviceEnvironmentRepositoryPort
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

class ValidateDownloadConditionsUseCaseTest {

    private lateinit var mockDeviceEnvironmentRepository: DeviceEnvironmentRepositoryPort
    private lateinit var useCase: ValidateDownloadConditionsUseCase

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0

        // Use non-relaxed mocks with proper every {} setup
        mockDeviceEnvironmentRepository = mockk()

        // Default mock behavior - return true for connected, false for storage
        coEvery { mockDeviceEnvironmentRepository.isWifiConnected() } returns true
        coEvery { mockDeviceEnvironmentRepository.hasRequiredStorage(any()) } returns true

        useCase = ValidateDownloadConditionsUseCase(mockDeviceEnvironmentRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `invoke allows download when no missing models`() = runTest {
        // When - empty list means no download needed
        val result = useCase(emptyList(), wifiOnly = false)

        // Then - should allow download immediately without checking wifi/storage
        assertTrue(result.canStart)
        assertEquals(null, result.errorMessage)
    }

    private fun createLocalModelAsset(modelType: ModelType, sha256: String): LocalModelAsset {
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                huggingFaceModelName = "test/model",
                remoteFileName = "${modelType.name.lowercase()}.litertlm",
                localFileName = "${modelType.name.lowercase()}.litertlm",
                sha256 = sha256,
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            configurations = listOf(
                LocalModelConfiguration(
                    localModelId = 1L,
                    displayName = "Test Config",
                    maxTokens = 2048,
                    contextWindow = 2048,
                    temperature = 0.7,
                    topP = 0.9,
                    topK = 40,
                    repetitionPenalty = 1.0,
                    systemPrompt = "You are helpful."
                )
            )
        )
    }

    @Test
    fun `invoke blocks download when wifiOnly enabled but not on WiFi`() = runTest {
        // Given
        coEvery { mockDeviceEnvironmentRepository.isWifiConnected() } returns false
        coEvery { mockDeviceEnvironmentRepository.hasRequiredStorage(any()) } returns true

        val missingModels = listOf(createLocalModelAsset(ModelType.MAIN, "abc123"))

        // When
        val result = useCase(missingModels, wifiOnly = true)

        // Then
        assertFalse(result.canStart)
        assertEquals("WiFi-only mode enabled but not connected to WiFi", result.errorMessage)
    }

    @Test
    fun `invoke allows download when wifiOnly enabled and on WiFi`() = runTest {
        // Given
        coEvery { mockDeviceEnvironmentRepository.isWifiConnected() } returns true
        coEvery { mockDeviceEnvironmentRepository.hasRequiredStorage(any()) } returns true

        val missingModels = listOf(createLocalModelAsset(ModelType.MAIN, "abc123"))

        // When
        val result = useCase(missingModels, wifiOnly = true)

        // Then
        assertTrue(result.canStart)
        assertEquals(null, result.errorMessage)
    }

    @Test
    fun `invoke blocks download when insufficient storage`() = runTest {
        // Given
        coEvery { mockDeviceEnvironmentRepository.isWifiConnected() } returns true
        coEvery { mockDeviceEnvironmentRepository.hasRequiredStorage(any()) } returns false

        val missingModels = listOf(createLocalModelAsset(ModelType.MAIN, "abc123"))

        // When
        val result = useCase(missingModels, wifiOnly = false)

        // Then
        assertFalse(result.canStart)
        assertTrue(result.errorMessage?.contains("Insufficient storage") == true)
    }

    @Test
    fun `invoke allows download with mobile data when wifiOnly disabled`() = runTest {
        // Given
        coEvery { mockDeviceEnvironmentRepository.isWifiConnected() } returns false
        coEvery { mockDeviceEnvironmentRepository.hasRequiredStorage(any()) } returns true

        val missingModels = listOf(createLocalModelAsset(ModelType.MAIN, "abc123"))

        // When
        val result = useCase(missingModels, wifiOnly = false)

        // Then
        assertTrue(result.canStart)
    }

    @Test
    fun `invoke returns missing models in result`() = runTest {
        // Given
        coEvery { mockDeviceEnvironmentRepository.isWifiConnected() } returns true
        coEvery { mockDeviceEnvironmentRepository.hasRequiredStorage(any()) } returns true

        val missingModels = listOf(
            createLocalModelAsset(ModelType.MAIN, "abc123"),
            createLocalModelAsset(ModelType.FAST, "def456")
        )

        // When
        val result = useCase(missingModels, wifiOnly = false)

        // Then
        assertEquals(2, result.missingModels.size)
        assertTrue(result.missingModels.any { it.metadata.sha256 == "abc123" })
        assertTrue(result.missingModels.any { it.metadata.sha256 == "def456" })
    }

    @Test
    fun `invoke checks wifi first then storage`() = runTest {
        // Given
        coEvery { mockDeviceEnvironmentRepository.isWifiConnected() } returns false
        // Storage check should not be called if WiFi check fails

        val missingModels = listOf(createLocalModelAsset(ModelType.MAIN, "abc123"))

        // When
        val result = useCase(missingModels, wifiOnly = true)

        // Then - should fail on WiFi check, not check storage
        assertFalse(result.canStart)
        coVerify { mockDeviceEnvironmentRepository.isWifiConnected() }
    }

    @Test
    fun `invoke with wifiOnly false does not check wifi connection`() = runTest {
        // Given - wifiOnly is false so we shouldn't care about wifi status
        coEvery { mockDeviceEnvironmentRepository.hasRequiredStorage(any()) } returns true

        val missingModels = listOf(createLocalModelAsset(ModelType.MAIN, "abc123"))

        // When
        val result = useCase(missingModels, wifiOnly = false)

        // Then
        assertTrue(result.canStart)
        coVerify { mockDeviceEnvironmentRepository.hasRequiredStorage(any()) }
    }

    // ============================================================================
    // Network Loss Scenarios
    // ============================================================================

    /**
     * Scenario: Complete network loss (no WiFi, no mobile)
     * When there's no network at all, downloads should be blocked
     * regardless of wifiOnly setting
     */
    @Test
    fun `invoke blocks when no network available at all`() = runTest {
        // Given - no network connection
        coEvery { mockDeviceEnvironmentRepository.isWifiConnected() } returns false
        coEvery { mockDeviceEnvironmentRepository.hasRequiredStorage(any()) } returns true

        val missingModels = listOf(createLocalModelAsset(ModelType.MAIN, "abc123"))

        // When - wifiOnly=true but no network
        val result = useCase(missingModels, wifiOnly = true)

        // Then - should block due to WiFi requirement
        assertFalse(result.canStart)
        assertTrue(result.errorMessage?.contains("WiFi") == true)
    }

    /**
     * Scenario: Mobile data available but wifiOnly=true
     * Downloads should be blocked (WiFi-only mode)
     */
    @Test
    fun `invoke blocks mobile data when wifiOnly is true`() = runTest {
        // Given - mobile data connected but not WiFi
        coEvery { mockDeviceEnvironmentRepository.isWifiConnected() } returns false
        coEvery { mockDeviceEnvironmentRepository.hasRequiredStorage(any()) } returns true

        val missingModels = listOf(createLocalModelAsset(ModelType.MAIN, "abc123"))

        // When - wifiOnly=true
        val result = useCase(missingModels, wifiOnly = true)

        // Then - should block because not on WiFi
        assertFalse(result.canStart)
        assertTrue(result.errorMessage?.contains("WiFi") == true)
    }

    /**
     * Scenario: Network available, wifiOnly=false
     * Downloads should proceed (even on mobile)
     */
    @Test
    fun `invoke allows download when network available and wifiOnly is false`() = runTest {
        // Given - mobile network available
        coEvery { mockDeviceEnvironmentRepository.isWifiConnected() } returns false
        coEvery { mockDeviceEnvironmentRepository.hasRequiredStorage(any()) } returns true

        val missingModels = listOf(createLocalModelAsset(ModelType.MAIN, "abc123"))

        // When - wifiOnly=false (user allowed mobile)
        val result = useCase(missingModels, wifiOnly = false)

        // Then - should allow
        assertTrue(result.canStart)
    }

    /**
     * Scenario: wifiOnly=false but still need storage check
     * Even with network available, storage must be checked
     */
    @Test
    fun `invoke checks storage even when network is available with wifiOnly false`() = runTest {
        // Given - network available but no storage
        coEvery { mockDeviceEnvironmentRepository.isWifiConnected() } returns false
        coEvery { mockDeviceEnvironmentRepository.hasRequiredStorage(any()) } returns false

        val missingModels = listOf(createLocalModelAsset(ModelType.MAIN, "abc123"))

        // When - wifiOnly=false
        val result = useCase(missingModels, wifiOnly = false)

        // Then - should block due to storage
        assertFalse(result.canStart)
        assertTrue(result.errorMessage?.contains("storage") == true)
    }
}
