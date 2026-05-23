package com.browntowndev.enclave.feature.chat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.PromptComposerPort
import com.browntowndev.enclave.domain.port.inference.SearchIntentClassifierPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.MemoriesRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import com.browntowndev.enclave.domain.usecase.chat.ChatGenerationProgressPersister
import com.browntowndev.enclave.domain.usecase.chat.ChatGenerationProgressSession
import com.browntowndev.enclave.domain.usecase.chat.ChatHistoryRehydrator
import com.browntowndev.enclave.domain.usecase.chat.ChatInferenceRequestPreparer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ChatInferenceService : Service() {
    companion object {
        const val TAG = "ChatInferenceService"
        const val ACTION_START = "com.browntowndev.enclave.chat.ACTION_START"
        const val ACTION_STOP = "com.browntowndev.enclave.chat.ACTION_STOP"

        const val EXTRA_PROMPT = "extra_prompt"
        const val EXTRA_USER_MESSAGE_ID = "extra_user_message_id"
        const val EXTRA_ASSISTANT_MESSAGE_ID = "extra_assistant_message_id"
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_USER_HAS_IMAGE = "extra_user_has_image"
        const val EXTRA_MODEL_TYPE = "extra_model_type"

        private const val ONGOING_NOTIFICATION_ID = 1001
        private const val COMPLETION_NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "chat_inference_channel"
        private const val COMPLETION_CHANNEL_ID = "chat_completion_channel"
    }

    @Inject
    lateinit var inferenceFactory: InferenceFactoryPort

    @Inject
    lateinit var activeModelProvider: ActiveModelProviderPort

    @Inject
    lateinit var messageRepository: MessageRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var memoriesRepository: MemoriesRepository

    @Inject
    lateinit var embeddingEnginePort: EmbeddingEnginePort

    @Inject
    lateinit var promptComposer: PromptComposerPort

    @Inject
    lateinit var loggingPort: LoggingPort

    @Inject
    lateinit var searchIntentClassifier: SearchIntentClassifierPort

    @Inject
    lateinit var toolExecutor: ToolExecutorPort

    @Inject
    lateinit var activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort

    @Inject
    lateinit var defaultModelRepository: com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort

    @Inject
    lateinit var mediaProviderRepository: com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort

    @Inject
    lateinit var mediaToolDefinitionFactory: com.browntowndev.enclave.domain.usecase.media.MediaToolDefinitionFactory

    @Inject
    lateinit var getProviderCapabilitiesUseCase: com.browntowndev.enclave.domain.usecase.media.GetProviderCapabilitiesUseCase

    @Inject
    lateinit var chatGenerationProgressPersister: ChatGenerationProgressPersister

    private lateinit var historyRehydrator: ChatHistoryRehydrator
    private lateinit var inferenceRequestPreparer: ChatInferenceRequestPreparer

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + job)
    private var currentJob: Job? = null
    private var currentChatId: ChatId? = null
    private var currentAssistantMessageId: MessageId? = null
    private var currentSession: ChatGenerationProgressSession? = null
    private var isStopping = false
    private var latestStartId: Int = -1

    override fun onCreate() {
        super.onCreate()
        loggingPort.info(TAG, "onCreate")
        createNotificationChannels()
        historyRehydrator = ChatHistoryRehydrator(messageRepository, loggingPort)
        inferenceRequestPreparer =
            ChatInferenceRequestPreparer(
                activeModelProvider,
                settingsRepository,
                messageRepository,
                memoriesRepository,
                embeddingEnginePort,
                promptComposer = promptComposer,
                defaultModelRepository,
                mediaProviderRepository,
                mediaToolDefinitionFactory,
                getProviderCapabilitiesUseCase,
                loggingPort,
                searchIntentClassifier = searchIntentClassifier,
                toolExecutor = toolExecutor,
            )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        latestStartId = startId
        loggingPort.info(
            TAG,
            "onStartCommand action=${intent?.action} flags=$flags startId=$startId hasIntent=${intent != null}",
        )
        when (intent?.action) {
            ACTION_START -> {
                isStopping = false
                val prompt = intent.getStringExtra(EXTRA_PROMPT) ?: ""
                val userMessageId = MessageId(intent.getStringExtra(EXTRA_USER_MESSAGE_ID) ?: "")
                val assistantMessageId = MessageId(intent.getStringExtra(EXTRA_ASSISTANT_MESSAGE_ID) ?: "")
                val chatId = ChatId(intent.getStringExtra(EXTRA_CHAT_ID) ?: "")
                val userHasImage = intent.getBooleanExtra(EXTRA_USER_HAS_IMAGE, false)
                val modelType = ModelType.valueOf(intent.getStringExtra(EXTRA_MODEL_TYPE) ?: ModelType.FAST.name)
                loggingPort.info(
                    TAG,
                    "ACTION_START chat=${chatId.value} " +
                        "assistantMessageId=${assistantMessageId.value} " +
                        "modelType=${modelType.name} userHasImage=$userHasImage",
                )

                startInference(prompt, userMessageId, assistantMessageId, chatId, userHasImage, modelType)
            }
            ACTION_STOP -> {
                loggingPort.info(TAG, "ACTION_STOP received")
                if (!isStopping) {
                    isStopping = true
                    stopInference()

                    // If no job is active, stop immediately. Otherwise, the job's finally block will handle it.
                    if (currentJob?.isActive != true) {
                        stopForeground(ChatInferenceNotificationPolicy.FOREGROUND_STOP_MODE)
                        stopSelf(latestStartId)
                    }
                } else {
                    loggingPort.info(TAG, "Already stopping, ignoring ACTION_STOP")
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInference(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        userHasImage: Boolean,
        modelType: ModelType,
    ) {
        currentJob?.cancel()
        currentChatId = chatId
        currentAssistantMessageId = assistantMessageId
        loggingPort.info(
            TAG,
            "startInference chat=${chatId.value} " +
                "assistantMessageId=${assistantMessageId.value} " +
                "modelType=${modelType.name} userHasImage=$userHasImage",
        )
        startForeground(ONGOING_NOTIFICATION_ID, createOngoingNotification(chatId))
        loggingPort.debug(TAG, "foreground service started chat=${chatId.value}")

        currentJob =
            serviceScope.launch {
                val persistenceSession =
                    chatGenerationProgressPersister.startSession(
                        mode = modelType.toSingleModelMode(),
                        chatId = chatId,
                        userMessageId = userMessageId,
                        assistantMessageId = assistantMessageId,
                    )
                currentSession = persistenceSession
                try {
                    loggingPort.debug(
                        TAG,
                        "service job started chat=${chatId.value} assistantMessageId=${assistantMessageId.value}",
                    )
                    if (userHasImage) {
                        loggingPort.debug(
                            TAG,
                            "emitting initial Processing state for image request " +
                                "chat=${chatId.value} assistantMessageId=${assistantMessageId.value}",
                        )
                        publishState(
                            chatId = chatId,
                            assistantMessageId = assistantMessageId,
                            state = MessageGenerationState.Processing(modelType),
                            persistenceSession = persistenceSession,
                        )
                    }
                    inferenceFactory.withInferenceService(modelType) { service ->
                        loggingPort.debug(
                            TAG,
                            "withInferenceService entered chat=${chatId.value} assistantMessageId=${assistantMessageId.value}",
                        )
                        generateWithService(
                            prompt = prompt,
                            userMessageId = userMessageId,
                            assistantMessageId = assistantMessageId,
                            chatId = chatId,
                            service = service,
                            modelType = modelType,
                            persistenceSession = persistenceSession,
                        )
                    }
                } catch (e: CancellationException) {
                    loggingPort.info(
                        TAG,
                        "Inference cancelled in service chat=${chatId.value} assistantMessageId=${assistantMessageId.value}",
                    )
                    withContext(NonCancellable) {
                        persistenceSession.flush(markIncompleteAsCancelled = true)
                        publishCancelledTerminalSnapshot(
                            chatId = chatId,
                            assistantMessageId = assistantMessageId,
                            persistenceSession = persistenceSession,
                        )
                    }
                    throw e
                } catch (e: ActiveModelConfigurationDecryptException) {
                    loggingPort.error(TAG, "Model configuration decrypt failed in service", e)
                    publishState(
                        chatId = chatId,
                        assistantMessageId = assistantMessageId,
                        state = ChatInferenceDecryptFailureMapper.toFailedState(e, modelType),
                        persistenceSession = persistenceSession,
                    )
                } catch (e: Exception) {
                    loggingPort.error(TAG, "Inference failed in service", e)
                    publishState(
                        chatId = chatId,
                        assistantMessageId = assistantMessageId,
                        state = MessageGenerationState.Failed(e, modelType),
                        persistenceSession = persistenceSession,
                    )
                } finally {
                    stopForeground(ChatInferenceNotificationPolicy.FOREGROUND_STOP_MODE)
                    stopSelf(latestStartId)
                }
            }
    }

    private suspend fun generateWithService(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        service: LlmInferencePort,
        modelType: ModelType,
        persistenceSession: ChatGenerationProgressSession,
    ) {
        val config = activeModelProvider.getActiveConfiguration(modelType)
        loggingPort.info(
            TAG,
            "generateWithService start chat=${chatId.value} " +
                "assistantMessageId=${assistantMessageId.value} " +
                "modelType=${modelType.name} configAvailable=${config != null}",
        )
        val preparedRequest =
            inferenceRequestPreparer(
                prompt = prompt,
                chatId = chatId,
                userMessageId = userMessageId,
                assistantMessageId = assistantMessageId,
                modelType = modelType,
            )

        if (preparedRequest.preRetrievalSources.isNotEmpty()) {
            publishState(
                chatId = chatId,
                assistantMessageId = assistantMessageId,
                state = MessageGenerationState.WebSearchSourcesAttached(
                    preparedRequest.preRetrievalSources,
                    modelType
                ),
                persistenceSession = persistenceSession,
            )
        }

        loggingPort.debug(
            TAG,
            "request prepared chat=${chatId.value} " +
                "assistantMessageId=${assistantMessageId.value} " +
                "promptLength=${preparedRequest.prompt.length}",
        )

        try {
            loggingPort.debug(TAG, "rehydrating history chat=${chatId.value} assistantMessageId=${assistantMessageId.value}")
            historyRehydrator(
                chatId = chatId,
                userMessageId = userMessageId,
                assistantMessageId = assistantMessageId,
                service = service,
            )
            loggingPort.debug(TAG, "history rehydration complete chat=${chatId.value} assistantMessageId=${assistantMessageId.value}")
        } catch (e: Exception) {
            loggingPort.debug(TAG, "Failed to rehydrate history: ${e.message}")
        }

        loggingPort.info(TAG, "sendPrompt begin chat=${chatId.value} assistantMessageId=${assistantMessageId.value}")
        service
            .sendPrompt(
                preparedRequest.prompt,
                preparedRequest.options,
                closeConversation = false,
            ).collect { event ->
                loggingPort.debug(
                    TAG,
                    "stream event chat=${chatId.value} assistantMessageId=${assistantMessageId.value} event=${event::class.simpleName}",
                )
                val state =
                    when (event) {
                        is InferenceEvent.EngineLoading -> MessageGenerationState.EngineLoading(event.modelType)
                        is InferenceEvent.Processing -> MessageGenerationState.Processing(event.modelType)
                        is InferenceEvent.Thinking -> MessageGenerationState.ThinkingLive(event.chunk, modelType)
                        is InferenceEvent.PartialResponse -> MessageGenerationState.GeneratingText(event.chunk, event.modelType)
                        is InferenceEvent.WebSearchResults ->
                            MessageGenerationState.WebSearchSourcesAttached(
                                event.sources,
                                event.modelType,
                            )
                        is InferenceEvent.Artifacts -> MessageGenerationState.ArtifactsAttached(event.artifacts, event.modelType)
                        is InferenceEvent.Media ->
                            MessageGenerationState.MediaAttached(
                                mediaUris = event.mediaUris,
                                mediaIds = event.mediaIds,
                                mediaTypes = event.mediaTypes,
                                prompts = event.prompts,
                                modelType = event.modelType,
                            )
                        is InferenceEvent.CompactionApplied -> null
                        is InferenceEvent.PreCompact -> null
                        is InferenceEvent.PostCompact -> null
                        is InferenceEvent.ToolRepaired -> null
                        is InferenceEvent.Finished -> {
                            maybeShowCompletionNotification(chatId)
                            MessageGenerationState.Finished(event.modelType)
                        }
                        is InferenceEvent.SafetyBlocked -> MessageGenerationState.Blocked(event.reason, event.modelType)
                        is InferenceEvent.Error -> MessageGenerationState.Failed(event.cause, event.modelType)
                    }
                publishState(
                    chatId = chatId,
                    assistantMessageId = assistantMessageId,
                    state = state ?: return@collect,
                    persistenceSession = persistenceSession,
                )
            }
        loggingPort.info(TAG, "sendPrompt complete chat=${chatId.value} assistantMessageId=${assistantMessageId.value}")
    }

    private suspend fun publishCancelledTerminalSnapshot(
        chatId: ChatId,
        assistantMessageId: MessageId,
        persistenceSession: ChatGenerationProgressSession,
    ) {
        val snapshot = persistenceSession.accumulatedMessages()
        if (snapshot.messages.isEmpty()) return
        val key = ActiveChatTurnKey(chatId, assistantMessageId)
        activeChatTurnSnapshotPort.publish(key, snapshot)
        loggingPort.debug(
            TAG,
            "Published cancelled terminal snapshot chat=${chatId.value} " +
                "assistantMessageId=${assistantMessageId.value} " +
                "state=${snapshot.messages[assistantMessageId]?.messageState}",
        )
    }

    private suspend fun publishState(
        chatId: ChatId,
        assistantMessageId: MessageId,
        state: MessageGenerationState,
        persistenceSession: ChatGenerationProgressSession,
    ) {
        val snapshot = persistenceSession.applyState(state)
        val snapMessage = snapshot.messages[assistantMessageId]
        loggingPort.debug(
            TAG,
            "publishState evt=${state::class.simpleName} " +
                "snapState=${snapMessage?.messageState} " +
                "segs=${snapMessage?.transcriptSegments?.size}",
        )
        activeChatTurnSnapshotPort.publish(
            key = ActiveChatTurnKey(chatId, assistantMessageId),
            snapshot = snapshot,
        )
    }

    private fun stopInference() {
        loggingPort.info(TAG, "stopInference requested")
        currentJob?.cancel()

        val chatId = currentChatId
        val assistantId = currentAssistantMessageId
        val session = currentSession

        serviceScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                session?.flush(markIncompleteAsCancelled = true)
                if (chatId != null && assistantId != null && session != null) {
                    publishCancelledTerminalSnapshot(
                        chatId = chatId,
                        assistantMessageId = assistantId,
                        persistenceSession = session,
                    )
                }
            }

            // Ensure service stops if this was the last action
            if (currentJob == null || currentJob?.isActive == false) {
                stopForeground(ChatInferenceNotificationPolicy.FOREGROUND_STOP_MODE)
                stopSelf(latestStartId)
            }
        }
    }

    private fun ModelType.toSingleModelMode(): Mode =
        when (this) {
            ModelType.THINKING -> Mode.THINKING
            else -> Mode.FAST
        }

    private fun createOngoingNotification(chatId: ChatId): Notification {
        val stopIntent =
            Intent(this, ChatInferenceService::class.java).apply {
                action = ACTION_STOP
            }
        val stopPendingIntent =
            PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE,
            )

        val deepLinkIntent =
            Intent(Intent.ACTION_VIEW, Uri.parse(ChatNotificationDeepLink.uriStringFor(chatId))).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                setPackage(packageName)
            }
        val deepLinkPendingIntent =
            PendingIntent.getActivity(
                this,
                chatId.value.hashCode(),
                deepLinkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle("Enclave")
            .setContentText("Generating AI response...")
            .setSmallIcon(android.R.drawable.ic_menu_edit) // Placeholder
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setContentIntent(deepLinkPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Shows the completion notification only when the app is not in the foreground.
     * Uses [ProcessLifecycleOwner] to check the app's lifecycle state, which avoids
     * showing a redundant notification when the user is actively viewing the chat.
     */
    private fun maybeShowCompletionNotification(chatId: ChatId) {
        val isForeground =
            ProcessLifecycleOwner
                .get()
                .lifecycle.currentState
                .isAtLeast(Lifecycle.State.STARTED)
        if (ChatInferenceNotificationPolicy.shouldShowCompletionNotification(isForeground)) {
            showCompletionNotification(chatId)
        } else {
            loggingPort.debug(TAG, "App is foregrounded; skipping completion notification")
        }
    }

    private fun showCompletionNotification(chatId: ChatId) {
        val deepLinkIntent =
            Intent(Intent.ACTION_VIEW, Uri.parse(ChatNotificationDeepLink.uriStringFor(chatId))).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                setPackage(packageName)
            }
        val deepLinkPendingIntent =
            PendingIntent.getActivity(
                this,
                chatId.value.hashCode(),
                deepLinkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(this, COMPLETION_CHANNEL_ID)
                .setContentTitle("Enclave")
                .setContentText("AI response complete.")
                .setSmallIcon(android.R.drawable.ic_menu_edit) // Placeholder
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(deepLinkPendingIntent)
                .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val ongoingChannel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Chat Inference",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Shows ongoing AI generation status"
                }
            manager.createNotificationChannel(ongoingChannel)

            val completionChannel =
                NotificationChannel(
                    COMPLETION_CHANNEL_ID,
                    "Inference Completion",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Notifies when AI response is ready"
                }
            manager.createNotificationChannel(completionChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
