package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.memory.Memory
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import com.browntowndev.pocketcrew.domain.usecase.FakeMemoriesRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("DeleteAllMemoriesUseCase")
class DeleteAllMemoriesUseCaseTest {
    private lateinit var memoriesRepository: FakeMemoriesRepository
    private lateinit var useCase: DeleteAllMemoriesUseCase

    @BeforeEach
    fun setup() {
        memoriesRepository = FakeMemoriesRepository()
        useCase = DeleteAllMemoriesUseCase(memoriesRepository)
    }

    @Nested
    inner class HappyPaths {
        @Test
        fun `S5 deletes four memories`() =
            runTest {
                memoriesRepository.setMemories(
                    listOf(
                        Memory(id = "mem-1", category = MemoryCategory.FACTS, content = "a"),
                        Memory(id = "mem-2", category = MemoryCategory.FACTS, content = "b"),
                        Memory(id = "mem-3", category = MemoryCategory.FACTS, content = "c"),
                        Memory(id = "mem-4", category = MemoryCategory.FACTS, content = "d"),
                    ),
                )
                useCase()
                assertEquals(setOf("mem-1", "mem-2", "mem-3", "mem-4"), memoriesRepository.deletedMemoryIds)
            }

        @Test
        fun `S6 empty memories produces no deletions`() =
            runTest {
                memoriesRepository.setMemories(emptyList())
                useCase()
                assertEquals(emptySet<String>(), memoriesRepository.deletedMemoryIds)
            }
    }

    @Nested
    inner class Errors {
        @Test
        fun `E3 propagates deleteMemory failures`() =
            runTest {
                memoriesRepository.setMemories(
                    listOf(Memory(id = "mem-X", category = MemoryCategory.FACTS, content = "x")),
                )
                memoriesRepository.deleteMemoryFailure = RuntimeException("db locked")
                val thrown =
                    assertThrows<RuntimeException> {
                        useCase()
                    }
                assertEquals("db locked", thrown.message)
            }
    }
}
