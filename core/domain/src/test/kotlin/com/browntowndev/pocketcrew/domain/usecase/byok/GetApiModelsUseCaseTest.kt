package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.usecase.FakeApiModelRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetApiModelsUseCaseTest {

    private lateinit var repository: FakeApiModelRepository
    private lateinit var useCase: GetApiModelsUseCase

    @BeforeEach
    fun setUp() {
        repository = FakeApiModelRepository()
        useCase = GetApiModelsUseCase(repository)
    }

    @Test
    fun `returns empty flow when no models configured`() = runTest {
        val result = useCase().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns all configured models`() = runTest {
        repository.save(ApiModelConfig(displayName = "Claude", provider = ApiProvider.ANTHROPIC, modelId = "m1"), "k1")
        repository.save(ApiModelConfig(displayName = "GPT", provider = ApiProvider.OPENAI, modelId = "m2"), "k2")
        repository.save(ApiModelConfig(displayName = "Gemini", provider = ApiProvider.GOOGLE, modelId = "m3"), "k3")

        val result = useCase().first()
        assertEquals(3, result.size)
        assertEquals(ApiProvider.ANTHROPIC, result[0].provider)
        assertEquals(ApiProvider.OPENAI, result[1].provider)
        assertEquals(ApiProvider.GOOGLE, result[2].provider)
    }
}
