package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ManageMemoriesParams
import com.browntowndev.pocketcrew.domain.model.inference.MemoryAction
import com.browntowndev.pocketcrew.domain.model.memory.Memory
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.repository.MemoriesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ManageMemoriesToolExecutorTest {

    private lateinit var loggingPort: LoggingPort
    private lateinit var memoriesRepository: MemoriesRepository
    private lateinit var embeddingEnginePort: EmbeddingEnginePort
    private lateinit var executor: ManageMemoriesToolExecutor

    @BeforeEach
    fun setup() {
        loggingPort = mockk(relaxed = true)
        memoriesRepository = mockk()
        embeddingEnginePort = mockk()
        executor = ManageMemoriesToolExecutor(
            loggingPort = loggingPort,
            memoriesRepository = memoriesRepository,
            embeddingEnginePort = embeddingEnginePort
        )
    }

    private fun createRequest(action: String, content: String? = null, category: String? = null, id: String? = null): ToolCallRequest {
        val args = mutableMapOf<String, String>()
        args["action"] = action.lowercase()
        content?.let { args["content"] = it }
        category?.let { args["category"] = it }
        id?.let { args["id"] = it }
        
        val argsJson = args.entries.joinToString(prefix = "{", postfix = "}") { 
            "\"${it.key}\": \"${it.value}\""
        }

        return ToolCallRequest(
            toolName = "manage_memories",
            argumentsJson = argsJson,
            provider = "OPENAI",
            modelType = ModelType.FAST
        )
    }

    @Test
    @DisplayName("Given save action, when executed, then calls insertMemory and returns success")
    fun testSaveAction() = runTest {
        val content = "User likes coffee"
        val category = "PREFERENCES"
        val memory = Memory(id = "mem-1", content = content, category = MemoryCategory.PREFERENCES)
        coEvery { memoriesRepository.insertMemory(MemoryCategory.PREFERENCES, content) } returns memory

        val request = createRequest(action = "save", content = content, category = category)
        val result = executor.execute(request)

        assertEquals("manage_memories", result.toolName)
        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals("success", json["status"]!!.jsonPrimitive.content)
        assertEquals("mem-1", json["id"]!!.jsonPrimitive.content)
        coVerify { memoriesRepository.insertMemory(MemoryCategory.PREFERENCES, content) }
    }

    @Test
    @DisplayName("Given update action, when executed, then calls updateMemory and returns success")
    fun testUpdateAction() = runTest {
        val content = "User likes tea"
        val id = "mem-1"
        coEvery { memoriesRepository.updateMemory(id, content) } returns Unit

        val request = createRequest(action = "update", content = content, id = id)
        val result = executor.execute(request)

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals("success", json["status"]!!.jsonPrimitive.content)
        coVerify { memoriesRepository.updateMemory(id, content) }
    }

    @Test
    @DisplayName("Given delete action, when executed, then calls deleteMemory and returns success")
    fun testDeleteAction() = runTest {
        val id = "mem-1"
        coEvery { memoriesRepository.deleteMemory(id) } returns Unit

        val request = createRequest(action = "delete", id = id)
        val result = executor.execute(request)

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals("success", json["status"]!!.jsonPrimitive.content)
        coVerify { memoriesRepository.deleteMemory(id) }
    }

    @Test
    @DisplayName("Given search action, when executed, then returns search results")
    fun testSearchAction() = runTest {
        val query = "find something"
        val request = createRequest(action = "search", content = query)
        val vector = floatArrayOf(0.1f, 0.2f)
        val memories = listOf(
            Memory(id = "m1", category = MemoryCategory.FACTS, content = "Found it")
        )
        coEvery { embeddingEnginePort.getEmbedding(query) } returns vector
        coEvery { memoriesRepository.searchMemories(vector) } returns memories
        
        val result = executor.execute(request)

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals("success", json["status"]!!.jsonPrimitive.content)
        val results = json["memories"]!!.jsonArray
        assertEquals(1, results.size)
        assertEquals("m1", results[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("Found it", results[0].jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("When exception occurs, then returns error status")
    fun testExceptionHandling() = runTest {
        coEvery { memoriesRepository.deleteMemory(any()) } throws RuntimeException("Delete failed")

        val request = createRequest(action = "delete", id = "mem-1")
        val result = executor.execute(request)

        val json = Json.parseToJsonElement(result.resultJson).jsonObject
        assertEquals("error", json["status"]!!.jsonPrimitive.content)
        assertEquals("Delete failed", json["message"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("When unsupported tool name, then throws IllegalArgumentException")
    fun testUnsupportedTool() = runTest {
        val request = ToolCallRequest(
            toolName = "wrong_tool",
            argumentsJson = "{}",
            provider = "OPENAI",
            modelType = ModelType.FAST
        )
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                executor.execute(request)
            }
        }
    }
}
