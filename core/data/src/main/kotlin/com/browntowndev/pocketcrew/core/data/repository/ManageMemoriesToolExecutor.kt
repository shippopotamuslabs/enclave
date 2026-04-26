package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.model.inference.ManageMemoriesParams
import com.browntowndev.pocketcrew.domain.model.inference.MemoryAction
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.repository.MemoriesRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ManageMemoriesResult(
    val status: String,
    val id: String? = null,
    val message: String? = null
)

@Serializable
private data class MemoryResult(
    val id: String,
    val category: String,
    val content: String
)

@Serializable
private data class ManageMemoriesSearchResult(
    val status: String,
    val memories: List<MemoryResult>
)

@Singleton
class ManageMemoriesToolExecutor @Inject constructor(
    private val loggingPort: LoggingPort,
    private val memoriesRepository: MemoriesRepository,
    private val embeddingEnginePort: EmbeddingEnginePort
) : ToolExecutorPort {

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        require(request.toolName == ToolDefinition.MANAGE_MEMORIES.name) { "Unsupported tool" }
        
        val params = request.parameters as ManageMemoriesParams
        val action = params.action
        val content = params.content ?: ""
        val categoryStr = params.category ?: "FACTS"
        val id = params.id ?: ""

        var resultJson = "{}"
        try {
            when (action) {
                MemoryAction.save -> {
                    val cat = try {
                        MemoryCategory.valueOf(categoryStr.uppercase())
                    } catch (e: Exception) {
                        MemoryCategory.FACTS
                    }
                    val mem = memoriesRepository.insertMemory(cat, content)
                    resultJson = Json.encodeToString(ManageMemoriesResult(status = "success", id = mem.id))
                }
                MemoryAction.update -> {
                    memoriesRepository.updateMemory(id, content)
                    resultJson = Json.encodeToString(ManageMemoriesResult(status = "success"))
                }
                MemoryAction.delete -> {
                    memoriesRepository.deleteMemory(id)
                    resultJson = Json.encodeToString(ManageMemoriesResult(status = "success"))
                }
                MemoryAction.search -> {
                    val queryVector = embeddingEnginePort.getEmbedding(content)
                    val memories = memoriesRepository.searchMemories(queryVector)
                    resultJson = Json.encodeToString(
                        ManageMemoriesSearchResult(
                            status = "success",
                            memories = memories.map {
                                MemoryResult(
                                    id = it.id,
                                    category = it.category.name,
                                    content = it.content
                                )
                            }
                        )
                    )
                }
            }
        } catch(e: Exception) {
            resultJson = Json.encodeToString(ManageMemoriesResult(status = "error", message = e.message))
        }

        return ToolExecutionResult(toolName = request.toolName, resultJson = resultJson)
    }
}
