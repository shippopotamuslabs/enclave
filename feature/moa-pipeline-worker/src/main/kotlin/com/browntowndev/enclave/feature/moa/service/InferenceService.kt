package com.browntowndev.enclave.feature.moa.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.annotation.VisibleForTesting
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineState
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.feature.inference.InferenceEventBus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Custom foreground Service for executing the Council ChatModeUi pipeline.
 * Uses `specialUse` foreground service type which has no quota limits
 * (unlike dataSync which has a 6-hour limit in any 24-hour period on Android 15+).
 *
 * This service:
 * 1. Runs as a foreground service for the entire pipeline (persistent notification)
 * 2. Executes all 4 steps: DRAFT_ONE -> DRAFT_TWO -> SYNTHESIS -> FINAL
 * 3. Emits progress via [InferenceEventBus] for real-time UI updates
 * 4. Handles cancellation via notification action
 */
@AndroidEntryPoint
class InferenceService : Service() {
    companion object {
        const val TAG = "InferenceService"
        const val ACTION_START = "com.browntowndev.enclave.inference.ACTION_START"
        const val ACTION_RESUME = "com.browntowndev.enclave.inference.ACTION_RESUME"
        const val ACTION_STOP = "com.browntowndev.enclave.inference.ACTION_STOP"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_USER_MESSAGE = "user_message"
        const val EXTRA_STATE_JSON = "state_json"

        private const val THERMAL_HEADROOM_FORECAST_SECONDS = 30
        private const val THERMAL_HEADROOM_SAFE_DEFAULT = 1.0f
        private const val THERMAL_SHUTDOWN_MIN_HEADROOM = 0.01f
        private const val THERMAL_SHUTDOWN_MAX_HEADROOM = 0.15f
    }

    @Inject
    lateinit var notificationManager: android.app.NotificationManager

    @Inject
    lateinit var logger: LoggingPort

    @Inject
    lateinit var inferenceFactoryProvider: dagger.Lazy<InferenceFactoryPort>

    @Inject
    lateinit var activeModelProvider: ActiveModelProviderPort

    @Inject
    lateinit var inferenceEventBus: InferenceEventBus

    @VisibleForTesting
    internal var serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentJob: Job? = null
    private var currentChatId: String? = null
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        CouncilPipelineNotifications.createNotificationChannels(notificationManager)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> handleStartCommand(intent)
            ACTION_RESUME -> handleResumeCommand(intent)
            ACTION_STOP -> handleStopCommand()
        }
        return START_NOT_STICKY
    }

    private fun handleStartCommand(intent: Intent) {
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID)
        val userMessage = intent.getStringExtra(EXTRA_USER_MESSAGE)
        val stateJson = intent.getStringExtra(EXTRA_STATE_JSON)

        if (chatId == null || userMessage == null) {
            logger.warning(TAG, "Missing required extras: chatId or userMessage")
            stopSelf()
            return
        }

        currentJob?.cancel()
        startPipelineForeground(PipelineStep.DRAFT_ONE, hasMoreSteps = true)

        isRunning = true
        currentChatId = chatId

        val state =
            try {
                stateJson?.let { PipelineState.fromJson(it) }
                    ?: PipelineState.createInitial(chatId, userMessage)
            } catch (e: Exception) {
                logger.error(TAG, "Failed to parse state: ${e.message}")
                PipelineState.createInitial(chatId, userMessage)
            }

        currentJob =
            serviceScope.launch {
                try {
                    executePipeline(chatId, state)
                } catch (e: CancellationException) {
                    logger.info(TAG, "Pipeline cancelled for chat: $chatId")
                    throw e
                } catch (e: Exception) {
                    logger.error(TAG, "Pipeline error for chat $chatId: ${e.message}", e)
                    inferenceEventBus.emitPipelineState(
                        chatId,
                        MessageGenerationState.Failed(
                            IllegalStateException(e.message ?: "Unknown error"),
                            CouncilPipelineStepSupport.getModelTypeForStep(state.currentStep),
                        ),
                    )
                } finally {
                    inferenceEventBus.clearPipelineRequest(chatId)
                    isRunning = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
    }

    private fun handleStopCommand() {
        logger.info(TAG, "Stop command received")
        currentJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleResumeCommand(intent: Intent) {
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID)
        val stateJson = intent.getStringExtra(EXTRA_STATE_JSON)

        if (chatId == null || stateJson == null) {
            logger.warning(TAG, "Missing required extras: chatId or stateJson for resume")
            stopSelf()
            return
        }

        val savedState =
            try {
                PipelineState.fromJson(stateJson)
            } catch (e: Exception) {
                logger.error(TAG, "Failed to parse saved state: ${e.message}")
                stopSelf()
                return
            }

        currentJob?.cancel()
        val currentStep = savedState.currentStep
        startPipelineForeground(currentStep, hasMoreSteps = currentStep.next() != null)

        isRunning = true
        currentChatId = chatId

        currentJob =
            serviceScope.launch {
                try {
                    executePipeline(chatId, savedState)
                } catch (e: CancellationException) {
                    logger.info(TAG, "Pipeline cancelled for chat: $chatId")
                    throw e
                } catch (e: Exception) {
                    logger.error(TAG, "Pipeline error for chat $chatId: ${e.message}", e)
                    inferenceEventBus.emitPipelineState(
                        chatId,
                        MessageGenerationState.Failed(
                            IllegalStateException(e.message ?: "Unknown error"),
                            CouncilPipelineStepSupport.getModelTypeForStep(savedState.currentStep),
                        ),
                    )
                } finally {
                    inferenceEventBus.clearPipelineRequest(chatId)
                    isRunning = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
    }

    override fun onDestroy() {
        currentJob?.cancel()
        currentChatId?.let { inferenceEventBus.clearPipelineRequest(it) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPipelineForeground(
        currentStep: PipelineStep,
        hasMoreSteps: Boolean,
    ) {
        val cancelIntent = createCancelPendingIntent()
        val notification =
            createProgressNotification(
                currentStep = currentStep,
                hasMoreSteps = hasMoreSteps,
                cancelPendingIntent = cancelIntent,
            )

        startForeground(
            CouncilPipelineNotifications.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private suspend fun executePipeline(
        chatId: String,
        initialState: PipelineState,
    ) {
        var currentState = initialState
        var currentStep = currentState.currentStep

        @Suppress("LoopWithTooManyJumpStatements")
        while (true) {
            logger.info(TAG, "Executing step: ${currentStep.name}")
            val prompt = CouncilPipelinePromptBuilder.buildPromptForStep(currentState, activeModelProvider)
            val modelType = CouncilPipelineStepSupport.getModelTypeForStep(currentStep)

            inferenceEventBus.emitPipelineState(
                chatId,
                MessageGenerationState.Processing(modelType),
            )

            val maxTokensOverride =
                CouncilPipelinePromptBuilder.calculateMaxTokensForStep(currentStep, activeModelProvider)
            val result = executeStepForPipeline(chatId, prompt, currentStep, maxTokensOverride)

            logger.info(TAG, "${currentStep.name} Response: ${result.output}")

            currentState = currentState.withStepOutput(currentStep, result.output)

            inferenceEventBus.emitPipelineState(
                chatId,
                MessageGenerationState.StepCompleted(
                    stepOutput = "",
                    modelDisplayName =
                        CouncilPipelineStepSupport.getModelDisplayNameForStep(
                            currentStep,
                            activeModelProvider,
                        ),
                    modelType = modelType,
                    stepType = currentStep,
                ),
            )

            if (currentStep == PipelineStep.FINAL) {
                logger.info(TAG, "Pipeline complete for chat: $chatId")
                inferenceEventBus.emitPipelineState(
                    chatId,
                    MessageGenerationState.Finished(modelType),
                )
                showCompletionNotification(currentState.durationSeconds())
                break
            }

            currentState = currentState.withNextStep() ?: break
            currentStep = currentState.currentStep
            updateProgressNotification(currentStep, hasMoreSteps = currentStep.next() != null)
        }
    }

    private suspend fun executeStepForPipeline(
        chatId: String,
        prompt: String,
        step: PipelineStep,
        maxTokensOverride: Int? = null,
    ): StepResult {
        var output = ""
        val modelType = CouncilPipelineStepSupport.getModelTypeForStep(step)

        inferenceFactoryProvider.get().withInferenceService(modelType) { service ->
            val config = activeModelProvider.getActiveConfiguration(modelType)

            val currentMaxTokens = config?.maxTokens ?: 1024
            val finalMaxTokens =
                if (maxTokensOverride != null) {
                    kotlin.math.min(maxTokensOverride, currentMaxTokens)
                } else {
                    currentMaxTokens
                }

            val hasImageTool = prompt.contains(ToolDefinition.ATTACHED_IMAGE_INSPECT.name)
            val options =
                GenerationOptions(
                    reasoningBudget = if (config?.thinkingEnabled == true) 1024 else 0,
                    modelType = modelType,
                    maxTokens = finalMaxTokens,
                    toolingEnabled = hasImageTool,
                    activeTools =
                        if (hasImageTool) {
                            listOf(ToolDefinition.ATTACHED_IMAGE_INSPECT)
                        } else {
                            emptyList()
                        },
                    chatId = ChatId(chatId),
                )

            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager

            service.sendPrompt(prompt, options = options, closeConversation = true).collect { event ->
                powerManager?.let { enforceThermalHeadroomOrAbort(it) }
                CouncilPipelineInferenceEventMapper.handlePipelineInferenceEvent(
                    event = event,
                    chatId = chatId,
                    step = step,
                    inferenceEventBus = inferenceEventBus,
                    onPartialChunk = { chunk ->
                        output += chunk
                    },
                )
            }
        }

        return StepResult(output = output)
    }

    private fun readThermalHeadroom(powerManager: android.os.PowerManager): Float =
        try {
            powerManager.getThermalHeadroom(THERMAL_HEADROOM_FORECAST_SECONDS)
        } catch (e: Exception) {
            logger.error(TAG, "getThermalHeadroom failed; assuming safe headroom", e)
            THERMAL_HEADROOM_SAFE_DEFAULT
        }

    private fun enforceThermalHeadroomOrAbort(powerManager: android.os.PowerManager) {
        val headroom = readThermalHeadroom(powerManager)
        if (headroom in THERMAL_SHUTDOWN_MIN_HEADROOM..THERMAL_SHUTDOWN_MAX_HEADROOM) {
            throw IllegalStateException("response was killed by Android due to thermal shutdown")
        }
    }

    @VisibleForTesting
    internal fun createCancelPendingIntent(): PendingIntent =
        CouncilPipelineNotifications.createCancelPendingIntent(this)

    @VisibleForTesting
    internal fun createProgressNotification(
        currentStep: PipelineStep,
        hasMoreSteps: Boolean,
        cancelPendingIntent: PendingIntent,
    ): Notification =
        CouncilPipelineNotifications.createProgressNotification(
            service = this,
            currentStep = currentStep,
            hasMoreSteps = hasMoreSteps,
            cancelPendingIntent = cancelPendingIntent,
        )

    private fun updateProgressNotification(
        currentStep: PipelineStep,
        hasMoreSteps: Boolean,
    ) {
        val cancelIntent = createCancelPendingIntent()
        val notification =
            createProgressNotification(
                currentStep = currentStep,
                hasMoreSteps = hasMoreSteps,
                cancelPendingIntent = cancelIntent,
            )
        notificationManager.notify(CouncilPipelineNotifications.NOTIFICATION_ID, notification)
    }

    private fun showCompletionNotification(thinkingDurationSeconds: Int) {
        CouncilPipelineNotifications.showCompletionNotification(
            service = this,
            notificationManager = notificationManager,
            thinkingDurationSeconds = thinkingDurationSeconds,
        )
    }

    private data class StepResult(
        val output: String,
    )
}
