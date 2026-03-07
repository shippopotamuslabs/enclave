package com.browntowndev.pocketcrew.domain.usecase.download

import android.util.Log
import com.browntowndev.pocketcrew.data.repository.DeviceEnvironmentRepository
import com.browntowndev.pocketcrew.domain.model.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.ModelType
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

class ValidateDownloadConditionsUseCaseTest {

    private lateinit var mockDeviceEnvironmentRepository: DeviceEnvironmentRepository
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
        every { mockDeviceEnvironmentRepository.isWifiConnected() } returns true
        every { mockDeviceEnvironmentRepository.hasRequiredStorage() } returns true

        useCase = ValidateDownloadConditionsUseCase(mockDeviceEnvironmentRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `invoke allows download when no missing models`() {
        // When - empty list means no download needed
        val result = useCase(emptyList(), wifiOnly = false)

        // Then - should allow download immediately without checking wifi/storage
        assertTrue(result.canStart)
        assertEquals(null, result.errorMessage)
    }

    private fun createModelConfig(modelType: ModelType, md5: String): ModelConfiguration {
        return ModelConfiguration(
            modelType = modelType,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = "test/model",
                remoteFileName = "${modelType.name.lowercase()}.litertlm",
                localFileName = "${modelType.name.lowercase()}.litertlm",
                displayName = "Test Model",
                md5 = md5,
                sizeInBytes = 1000000L,
                modelFileFormat = ModelFileFormat.LITERTLM
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = 0.7,
                topK = 40,
                topP = 0.9,
                maxTokens = 2048
            ),
            persona = ModelConfiguration.Persona(
                systemPrompt = "You are helpful."
            )
        )
    }

    @Test
    fun `invoke blocks download when wifiOnly enabled but not on WiFi`() {
        // Given
        every { mockDeviceEnvironmentRepository.isWifiConnected() } returns false
        every { mockDeviceEnvironmentRepository.hasRequiredStorage() } returns true

        val missingModels = listOf(createModelConfig(ModelType.MAIN, "abc123"))

        // When
        val result = useCase(missingModels, wifiOnly = true)

        // Then
        assertFalse(result.canStart)
        assertEquals("WiFi-only mode enabled but not connected to WiFi", result.errorMessage)
    }

    @Test
    fun `invoke allows download when wifiOnly enabled and on WiFi`() {
        // Given
        every { mockDeviceEnvironmentRepository.isWifiConnected() } returns true
        every { mockDeviceEnvironmentRepository.hasRequiredStorage() } returns true

        val missingModels = listOf(createModelConfig(ModelType.MAIN, "abc123"))

        // When
        val result = useCase(missingModels, wifiOnly = true)

        // Then
        assertTrue(result.canStart)
        assertEquals(null, result.errorMessage)
    }

    @Test
    fun `invoke blocks download when insufficient storage`() {
        // Given
        every { mockDeviceEnvironmentRepository.isWifiConnected() } returns true
        every { mockDeviceEnvironmentRepository.hasRequiredStorage() } returns false

        val missingModels = listOf(createModelConfig(ModelType.MAIN, "abc123"))

        // When
        val result = useCase(missingModels, wifiOnly = false)

        // Then
        assertFalse(result.canStart)
        assertTrue(result.errorMessage?.contains("Insufficient storage") == true)
    }

    @Test
    fun `invoke allows download with mobile data when wifiOnly disabled`() {
        // Given
        every { mockDeviceEnvironmentRepository.isWifiConnected() } returns false
        every { mockDeviceEnvironmentRepository.hasRequiredStorage() } returns true

        val missingModels = listOf(createModelConfig(ModelType.MAIN, "abc123"))

        // When
        val result = useCase(missingModels, wifiOnly = false)

        // Then
        assertTrue(result.canStart)
    }

    @Test
    fun `invoke returns missing models in result`() {
        // Given
        every { mockDeviceEnvironmentRepository.isWifiConnected() } returns true
        every { mockDeviceEnvironmentRepository.hasRequiredStorage() } returns true

        val missingModels = listOf(
            createModelConfig(ModelType.MAIN, "abc123"),
            createModelConfig(ModelType.FAST, "def456")
        )

        // When
        val result = useCase(missingModels, wifiOnly = false)

        // Then
        assertEquals(2, result.missingModels.size)
        assertTrue(result.missingModels.any { it.metadata.md5 == "abc123" })
        assertTrue(result.missingModels.any { it.metadata.md5 == "def456" })
    }

    @Test
    fun `invoke checks wifi first then storage`() {
        // Given
        every { mockDeviceEnvironmentRepository.isWifiConnected() } returns false
        // Storage check should not be called if WiFi check fails

        val missingModels = listOf(createModelConfig(ModelType.MAIN, "abc123"))

        // When
        val result = useCase(missingModels, wifiOnly = true)

        // Then - should fail on WiFi check, not check storage
        assertFalse(result.canStart)
        verify { mockDeviceEnvironmentRepository.isWifiConnected() }
    }

    @Test
    fun `invoke with wifiOnly false does not check wifi connection`() {
        // Given - wifiOnly is false so we shouldn't care about wifi status
        every { mockDeviceEnvironmentRepository.hasRequiredStorage() } returns true

        val missingModels = listOf(createModelConfig(ModelType.MAIN, "abc123"))

        // When
        val result = useCase(missingModels, wifiOnly = false)

        // Then
        assertTrue(result.canStart)
        verify { mockDeviceEnvironmentRepository.hasRequiredStorage() }
    }
}
