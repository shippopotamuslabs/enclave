package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeMemoriesRepository
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.memory.Memory
import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import kotlinx.coroutines.flow.first
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
    private lateinit var memoriesRepository: FakeMemoriesRepository
    private lateinit var embeddingEnginePort: FakeEmbeddingEngine
    private lateinit var executor: ManageMemoriesToolExecutor

    @BeforeEach
    fun setup() {
        memoriesRepository = FakeMemoriesRepository()
        embeddingEnginePort = FakeEmbeddingEngine()
        executor =
            ManageMemoriesToolExecutor(
                memoriesRepository = memoriesRepository,
                embeddingEnginePort = embeddingEnginePort,
            )
    }

    private fun createRequest(
        action: String,
        content: String? = null,
        category: String? = null,
        id: String? = null,
    ): ToolCallRequest {
        val args = mutableMapOf<String, String>()
        args["action"] = action.lowercase()
        content?.let { args["content"] = it }
        category?.let { args["category"] = it }
        id?.let { args["id"] = it }

        val argsJson =
            args.entries.joinToString(prefix = "{", postfix = "}") {
                "\"${it.key}\": \"${it.value}\""
            }

        return ToolCallRequest(
            toolName = "manage_memories",
            argumentsJson = argsJson,
            provider = "OPENAI",
            modelType = ModelType.FAST,
        )
    }

    @Test
    @DisplayName("Given save action, when executed, then calls insertMemory and returns success")
    fun testSaveAction() =
        runTest {
            val content = "User likes coffee"
            val category = "PREFERENCES"

            val request = createRequest(action = "save", content = content, category = category)
            val result = executor.execute(request)

            assertEquals("manage_memories", result.toolName)
            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            assertEquals("success", json["status"]!!.jsonPrimitive.content)
            val stored = memoriesRepository.getAllMemoriesFlow().first().single()
            assertEquals(stored.id, json["id"]!!.jsonPrimitive.content)
            assertEquals(listOf(MemoryCategory.PREFERENCES to content), memoriesRepository.insertCalls)
        }

    @Test
    @DisplayName("Given update action, when executed, then calls updateMemory and returns success")
    fun testUpdateAction() =
        runTest {
            val content = "User likes tea"
            val id = "mem-1"

            val request = createRequest(action = "update", content = content, id = id)
            val result = executor.execute(request)

            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            assertEquals("success", json["status"]!!.jsonPrimitive.content)
            assertEquals(listOf(Triple(id, content, null)), memoriesRepository.updateCalls)
        }

    @Test
    @DisplayName("Given delete action, when executed, then calls deleteMemory and returns success")
    fun testDeleteAction() =
        runTest {
            val id = "mem-1"

            val request = createRequest(action = "delete", id = id)
            val result = executor.execute(request)

            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            assertEquals("success", json["status"]!!.jsonPrimitive.content)
            assertEquals(listOf(id), memoriesRepository.deleteCalls)
        }

    @Test
    @DisplayName("Given search action, when executed, then returns search results")
    fun testSearchAction() =
        runTest {
            val query = "find something"
            val request = createRequest(action = "search", content = query)
            memoriesRepository.seed(
                listOf(
                    Memory(id = "m1", category = MemoryCategory.FACTS, content = "Found it"),
                ),
            )

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
    fun testExceptionHandling() =
        runTest {
            memoriesRepository.deleteFailure = RuntimeException("Delete failed")

            val request = createRequest(action = "delete", id = "mem-1")
            val result = executor.execute(request)

            val json = Json.parseToJsonElement(result.resultJson).jsonObject
            assertEquals("error", json["status"]!!.jsonPrimitive.content)
            assertEquals("Delete failed", json["message"]!!.jsonPrimitive.content)
        }

    @Test
    @DisplayName("When unsupported tool name, then throws IllegalArgumentException")
    fun testUnsupportedTool() =
        runTest {
            val request =
                ToolCallRequest(
                    toolName = "wrong_tool",
                    argumentsJson = "{}",
                    provider = "OPENAI",
                    modelType = ModelType.FAST,
                )
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking {
                    executor.execute(request)
                }
            }
        }
}
