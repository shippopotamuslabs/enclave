package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionEvent
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Composite tool executor that dispatches tool execution requests
 * to the appropriate executor based on the tool name.
 *
 * Routes:
 * - [ToolDefinition.TAVILY_WEB_SEARCH] → [SearchToolExecutorImpl]
 * - [ToolDefinition.TAVILY_EXTRACT] → [ExtractToolExecutorImpl]
 * - [ToolDefinition.ATTACHED_IMAGE_INSPECT] → [ImageInspectToolExecutor]
 * - [ToolDefinition.SEARCH_CHAT_HISTORY] → [SearchChatHistoryToolExecutor]
 * - [ToolDefinition.SEARCH_CHAT] → [SearchChatToolExecutor]
 * - [ToolDefinition.GET_MESSAGE_CONTEXT] → [GetMessageContextToolExecutor]
 */
@Singleton
class CompositeToolExecutor @Inject constructor(
    private val searchToolExecutor: SearchToolExecutorImpl,
    private val extractToolExecutor: ExtractToolExecutorImpl,
    private val imageInspectToolExecutor: ImageInspectToolExecutor,
    private val searchChatHistoryToolExecutor: SearchChatHistoryToolExecutor,
    private val searchChatToolExecutor: SearchChatToolExecutor,
    private val getMessageContextToolExecutor: GetMessageContextToolExecutor,
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
                modelType = request.modelType
            )
        )

        return try {
            val result = when (request.toolName) {
                ToolDefinition.TAVILY_WEB_SEARCH.name -> searchToolExecutor.execute(request)
                ToolDefinition.TAVILY_EXTRACT.name -> extractToolExecutor.execute(request)
                ToolDefinition.ATTACHED_IMAGE_INSPECT.name -> imageInspectToolExecutor.execute(request)
                ToolDefinition.SEARCH_CHAT_HISTORY.name -> searchChatHistoryToolExecutor.execute(request)
                ToolDefinition.SEARCH_CHAT.name -> searchChatToolExecutor.execute(request)
                ToolDefinition.GET_MESSAGE_CONTEXT.name -> getMessageContextToolExecutor.execute(request)
                else -> throw IllegalArgumentException("Unsupported tool: ${request.toolName}")
            }
            
            eventBus.emit(
                ToolExecutionEvent.Finished(
                    eventId = eventId,
                    chatId = request.chatId,
                    userMessageId = request.userMessageId,
                    resultJson = result.resultJson
                )
            )
            
            result
        } catch (e: Exception) {
            eventBus.emit(
                ToolExecutionEvent.Finished(
                    eventId = eventId,
                    chatId = request.chatId,
                    userMessageId = request.userMessageId,
                    error = e.message ?: "Unknown error"
                )
            )
            throw e
        }
    }
}