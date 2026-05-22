package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.artifact.ArtifactToolExecutor
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionEvent
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Composite tool executor that dispatches tool execution requests
 * to the appropriate executor based on the tool name.
 *
 * Routes:
 * - [ToolDefinition.WEB_SEARCH] → [SearchToolExecutorImpl]
 * - [ToolDefinition.WEB_EXTRACT] → [ExtractToolExecutorImpl]
 * - [ToolDefinition.ATTACHED_IMAGE_INSPECT] → [ImageInspectToolExecutor]
 * - [ToolDefinition.SEARCH_CHAT_HISTORY] → [SearchChatHistoryToolExecutor]
 * - [ToolDefinition.SEARCH_CHAT] → [SearchChatToolExecutor]
 * - [ToolDefinition.GET_MESSAGE_CONTEXT] → [GetMessageContextToolExecutor]
 * - [ToolDefinition.MANAGE_MEMORIES] → [ManageMemoriesToolExecutor]
 * - [ToolDefinition.START_ARTIFACT] → [ArtifactToolExecutor]
 * - [ToolDefinition.APPEND_BLOCKS] → [ArtifactToolExecutor]
 * - [ToolDefinition.SEARCH_CURRENT_ARTIFACT] → [SearchArtifactToolExecutor]
 * - [ToolDefinition.X_SEARCH] → [XSearchToolExecutorImpl]
 */
@Singleton
class CompositeToolExecutor
    @Inject
    constructor(
        private val searchToolExecutor: SearchToolExecutorImpl,
        private val extractToolExecutor: ExtractToolExecutorImpl,
        private val imageInspectToolExecutor: ImageInspectToolExecutor,
        private val searchChatHistoryToolExecutor: SearchChatHistoryToolExecutor,
        private val searchChatToolExecutor: SearchChatToolExecutor,
        private val getMessageContextToolExecutor: GetMessageContextToolExecutor,
        private val manageMemoriesToolExecutor: ManageMemoriesToolExecutor,
        private val artifactToolExecutor: ArtifactToolExecutor,
        private val searchArtifactToolExecutor: com.browntowndev.enclave.core.data.artifact.SearchArtifactToolExecutor,
        private val xSearchToolExecutor: XSearchToolExecutorImpl,
        private val mediaToolExecutor: MediaToolExecutor,
        private val eventBus: ToolExecutionEventBus,
    ) : ToolExecutorPort {
        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
            val eventId = UUID.randomUUID().toString()

            eventBus.emit(
                ToolExecutionEvent.Started(
                    eventId = eventId,
                    toolName = request.toolName,
                    argumentsJson = request.argumentsJson,
                    chatId = request.chatId,
                    userMessageId = request.userMessageId,
                    modelType = request.modelType,
                ),
            )

            return try {
                val result =
                    when (request.toolName) {
                        ToolDefinition.WEB_SEARCH.name -> searchToolExecutor.execute(request)
                        ToolDefinition.WEB_EXTRACT.name -> extractToolExecutor.execute(request)
                        ToolDefinition.ATTACHED_IMAGE_INSPECT.name -> imageInspectToolExecutor.execute(request)
                        ToolDefinition.SEARCH_CHAT_HISTORY.name -> searchChatHistoryToolExecutor.execute(request)
                        ToolDefinition.SEARCH_CHAT.name -> searchChatToolExecutor.execute(request)
                        ToolDefinition.GET_MESSAGE_CONTEXT.name -> getMessageContextToolExecutor.execute(request)
                        ToolDefinition.MANAGE_MEMORIES.name -> manageMemoriesToolExecutor.execute(request)
                        ToolDefinition.START_ARTIFACT.name -> artifactToolExecutor.execute(request)
                        ToolDefinition.APPEND_BLOCKS.name -> artifactToolExecutor.execute(request)
                        ToolDefinition.SEARCH_CURRENT_ARTIFACT.name -> searchArtifactToolExecutor.execute(request)
                        ToolDefinition.X_SEARCH.name -> xSearchToolExecutor.execute(request)
                        ToolDefinition.GENERATE_IMAGE.name -> mediaToolExecutor.execute(request)
                        ToolDefinition.GENERATE_VIDEO.name -> mediaToolExecutor.execute(request)
                        ToolDefinition.GENERATE_MUSIC.name -> mediaToolExecutor.execute(request)
                        else -> throw IllegalArgumentException("Unsupported tool: ${request.toolName}")
                    }

                eventBus.emit(
                    ToolExecutionEvent.Finished(
                        eventId = eventId,
                        chatId = request.chatId,
                        userMessageId = request.userMessageId,
                        resultJson = result.resultJson,
                    ),
                )

                result
            } catch (e: Exception) {
                eventBus.emit(
                    ToolExecutionEvent.Finished(
                        eventId = eventId,
                        chatId = request.chatId,
                        userMessageId = request.userMessageId,
                        error = e.message ?: "Unknown error",
                    ),
                )
                throw e
            }
        }
    }
