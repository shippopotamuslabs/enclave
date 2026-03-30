package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GetLocalModelAssetsUseCaseTest {
    private val repository = mockk<ModelRegistryPort>()
    private lateinit var useCase: GetLocalModelAssetsUseCase

    @BeforeEach
    fun setup() {
        useCase = GetLocalModelAssetsUseCaseImpl(repository)
    }

    @Test
    fun `invoke returns stream of local model assets`() = runTest {
        val assets = listOf(mockk<LocalModelAsset>())
        every { repository.observeAssets() } returns flowOf(assets)

        val result = useCase().first()

        assertEquals(1, result.size)
        assertEquals(assets, result)
    }
}