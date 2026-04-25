package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.FakeDefaultModelRepository
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class SetDefaultModelUseCaseTest {

    private lateinit var repository: FakeDefaultModelRepository
    private lateinit var localModelRepository: LocalModelRepositoryPort
    private lateinit var apiModelRepository: ApiModelRepositoryPort
    private lateinit var useCase: SetDefaultModelUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeDefaultModelRepository()
        localModelRepository = mockk(relaxed = true)
        apiModelRepository = mockk(relaxed = true)
        useCase = SetDefaultModelUseCaseImpl(repository, localModelRepository, apiModelRepository)
    }

    @Test
    fun `set FAST slot to API source`() = runTest {
        useCase(ModelType.FAST, localConfigId = null, apiConfigId = ApiModelConfigurationId("5"))

        val assignment = repository.getDefault(ModelType.FAST)
        assertEquals(ApiModelConfigurationId("5"), assignment?.apiConfigId)
    }

    @Test
    fun `set THINKING slot back to ON_DEVICE`() = runTest {
        useCase(ModelType.THINKING, localConfigId = LocalModelConfigurationId("1"), apiConfigId = null)

        val assignment = repository.getDefault(ModelType.THINKING)
        assertEquals(LocalModelConfigurationId("1"), assignment?.localConfigId)
    }

    @Test
    fun `set TTS slot to TTS provider`() = runTest {
        val ttsId = TtsProviderId("tts-1")
        useCase(ModelType.TTS, localConfigId = null, apiConfigId = null, ttsProviderId = ttsId)

        val assignment = repository.getDefault(ModelType.TTS)
        assertEquals(ttsId, assignment?.ttsProviderId)
    }

    @Test
    fun `set VISION slot rejects local model assignments`() = runTest {
        val error = assertFailsWith<IllegalArgumentException> {
            useCase(ModelType.VISION, localConfigId = LocalModelConfigurationId("vision-local"), apiConfigId = null)
        }

        assertEquals("Vision slot is API-only.", error.message)
    }
}
