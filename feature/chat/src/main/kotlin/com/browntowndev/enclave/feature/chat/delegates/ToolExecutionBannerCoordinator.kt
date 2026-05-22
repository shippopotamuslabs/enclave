package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.AppendBlocksParams
import com.browntowndev.enclave.domain.model.inference.StartArtifactParams
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionEvent
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.util.WebSearchResultParser
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi
import com.browntowndev.enclave.feature.chat.mapping.ToolCallBannerMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class ToolExecutionBannerCoordinator
    @Inject
    constructor(
        private val toolExecutionEventPort: ToolExecutionEventPort,
        private val loggingPort: LoggingPort,
        private val activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort,
    ) {
        private val _activeToolCallBanner = MutableStateFlow<ToolCallBannerUi?>(null)
        val activeToolCallBanner: StateFlow<ToolCallBannerUi?> = _activeToolCallBanner.asStateFlow()

        private val activeToolIds = mutableSetOf<String>()
        private val activeToolJobs = mutableMapOf<String, Job>()
        private val toolStartTimes = mutableMapOf<String, Long>()
        private val activeToolNames = mutableMapOf<String, String>()
        private val pendingStartArtifacts = mutableMapOf<String, PendingStartArtifact>()

        private data class PendingStartArtifact(
            val title: String,
        )

        fun start(
            scope: CoroutineScope,
            currentChatId: () -> ChatId?,
            initialChatId: ChatId?,
            activeTurnKey: () -> ActiveChatTurnKey?,
            onExtractingUrl: (String) -> Unit,
        ) {
            toolExecutionEventPort.events
                .onEach { event ->
                    handleEvent(
                        scope = scope,
                        event = event,
                        currentChatId = currentChatId() ?: initialChatId,
                        activeTurnKey = activeTurnKey,
                        onExtractingUrl = onExtractingUrl,
                    )
                }.launchIn(scope)
        }

        fun clearBanner() {
            activeToolJobs.values.forEach { job -> job.cancel() }
            activeToolJobs.clear()
            activeToolIds.clear()
            activeToolNames.clear()
            toolStartTimes.clear()
            pendingStartArtifacts.clear()
            _activeToolCallBanner.value = null
        }

        private fun handleEvent(
            scope: CoroutineScope,
            event: ToolExecutionEvent,
            currentChatId: ChatId?,
            activeTurnKey: () -> ActiveChatTurnKey?,
            onExtractingUrl: (String) -> Unit,
        ) {
            val turnChatId = event.chatId
            loggingPort.debug(
                TAG,
                "Received tool execution event: type=${event::class.java.simpleName} " +
                    "eventId=${event.eventId} turnChatId=${turnChatId?.value} " +
                    "currentChatId=${currentChatId?.value}",
            )
            if (turnChatId != currentChatId) return

                    when (event) {
                is ToolExecutionEvent.Started -> onToolStarted(event)
                is ToolExecutionEvent.Extracting -> onToolExtracting(event, onExtractingUrl)
                is ToolExecutionEvent.Finished -> onToolFinished(scope, event, activeTurnKey)
            }
        }

        private fun onToolStarted(event: ToolExecutionEvent.Started) {
            activeToolJobs[event.eventId]?.cancel()
            activeToolIds.add(event.eventId)
            toolStartTimes[event.eventId] = System.currentTimeMillis()
            val toolName = event.toolName.lowercase()
            activeToolNames[event.eventId] = toolName
            _activeToolCallBanner.value = ToolCallBannerMapper.toStartedBanner(toolName)
            parseStartArtifactIfNeeded(event, toolName)
            parseAppendBlocksIfNeeded(event, toolName)
        }

        private fun parseStartArtifactIfNeeded(
            event: ToolExecutionEvent.Started,
            toolName: String,
        ) {
            if (toolName != ToolDefinition.START_ARTIFACT.name) return
            runCatching {
                val params = jsonParser.decodeFromString<StartArtifactParams>(event.argumentsJson)
                pendingStartArtifacts[event.eventId] = PendingStartArtifact(title = params.title)
            }.onFailure { error ->
                loggingPort.error(TAG, "Failed to parse start_artifact request", error)
            }
        }

        private fun parseAppendBlocksIfNeeded(
            event: ToolExecutionEvent.Started,
            toolName: String,
        ) {
            if (toolName != ToolDefinition.APPEND_BLOCKS.name) return
            runCatching {
                jsonParser.decodeFromString<AppendBlocksParams>(event.argumentsJson)
            }.onFailure { error ->
                loggingPort.error(TAG, "Failed to parse append_blocks request", error)
            }
        }

        private fun onToolExtracting(
            event: ToolExecutionEvent.Extracting,
            onExtractingUrl: (String) -> Unit,
        ) {
            _activeToolCallBanner.value = ToolCallBannerMapper.toExtractingBanner(event.url)
            onExtractingUrl(event.url)
        }

        private fun onToolFinished(
            scope: CoroutineScope,
            event: ToolExecutionEvent.Finished,
            activeTurnKey: () -> ActiveChatTurnKey?,
        ) {
            val startTime = toolStartTimes[event.eventId] ?: return
            val elapsed = System.currentTimeMillis() - startTime
            val remaining = (MIN_BANNER_VISIBLE_MS - elapsed).coerceAtLeast(0)
            val capturedKey = activeTurnKey()
            val capturedStart = pendingStartArtifacts[event.eventId]
            val toolNameAtFinish = (activeToolNames[event.eventId] ?: "").lowercase()
            val sources =
                event.resultJson?.let { raw ->
                    WebSearchResultParser.parse(MessageId("temp"), raw)
                } ?: emptyList()
            val artifactId = parseArtifactIdFromResult(event.resultJson)

            activeToolJobs[event.eventId] =
                scope.launch {
                    if (sources.isNotEmpty()) {
                        _activeToolCallBanner.update { banner ->
                            banner?.copy(webSearchSources = sources)
                        }
                    }
                    attachArtifactIfNeeded(
                        toolNameAtFinish = toolNameAtFinish,
                        capturedKey = capturedKey,
                        capturedStart = capturedStart,
                        artifactId = artifactId,
                        eventId = event.eventId,
                        scope = scope,
                    )
                    delay(remaining)
                    finishTool(event.eventId)
                }
        }

        private fun shouldAttachStartedArtifact(
            toolNameAtFinish: String,
            capturedKey: ActiveChatTurnKey?,
            capturedStart: PendingStartArtifact?,
            artifactId: String?,
        ): Boolean =
            toolNameAtFinish == ToolDefinition.START_ARTIFACT.name &&
                capturedKey != null &&
                capturedStart != null &&
                artifactId != null

        private fun attachArtifactIfNeeded(
            toolNameAtFinish: String,
            capturedKey: ActiveChatTurnKey?,
            capturedStart: PendingStartArtifact?,
            artifactId: String?,
            eventId: String,
            scope: CoroutineScope,
        ) {
            if (!shouldAttachStartedArtifact(toolNameAtFinish, capturedKey, capturedStart, artifactId)) {
                return
            }
            val resolvedKey = requireNotNull(capturedKey)
            val resolvedStart = requireNotNull(capturedStart)
            val resolvedArtifactId = requireNotNull(artifactId)
            val artifact =
                ArtifactGenerationRequest(
                    artifactId = resolvedArtifactId,
                    title = resolvedStart.title,
                    content = "",
                )
            scope.launch {
                activeChatTurnSnapshotPort.attachArtifact(
                    key = resolvedKey,
                    assistantMessageId = resolvedKey.assistantMessageId,
                    artifact = artifact,
                )
            }
            pendingStartArtifacts.remove(eventId)
        }

        private fun finishTool(eventId: String) {
            activeToolIds.remove(eventId)
            activeToolNames.remove(eventId)
            if (activeToolIds.isEmpty()) {
                _activeToolCallBanner.value = null
            }
            toolStartTimes.remove(eventId)
            activeToolJobs.remove(eventId)
        }

        private fun parseArtifactIdFromResult(resultJson: String?): String? =
            resultJson
                ?.let { raw ->
                    runCatching {
                        jsonParser
                            .parseToJsonElement(raw)
                            .jsonObject["artifactId"]
                            ?.jsonPrimitive
                            ?.content
                    }.getOrNull()
                }?.takeIf { it.isNotBlank() }

        private companion object {
            private const val TAG = "ToolExecutionBannerCoordinator"
            private const val MIN_BANNER_VISIBLE_MS = 1000L
            private val jsonParser = Json { ignoreUnknownKeys = true }
        }
    }
