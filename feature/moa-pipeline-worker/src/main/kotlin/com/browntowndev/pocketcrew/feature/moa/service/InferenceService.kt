package com.browntowndev.pocketcrew.feature.moa.service

import android.app.Notification
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.inference.DraftOneModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.DraftTwoModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.FinalSynthesizerModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.MainModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineState
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.feature.inference.InferenceEventBus
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
 * Custom foreground Service for executing the Crew ChatModeUi pipeline.
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
        const val ACTION_START = "com.browntowndev.pocketcrew.inference.ACTION_START"
        const val ACTION_RESUME = "com.browntowndev.pocketcrew.inference.ACTION_RESUME"
        const val ACTION_STOP = "com.browntowndev.pocketcrew.inference.ACTION_STOP"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_USER_MESSAGE = "user_message"
        const val EXTRA_STATE_JSON = "state_json"
    }

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var logger: LoggingPort

    @Inject
    lateinit var inferenceFactoryProvider: dagger.Lazy<InferenceFactoryPort>

    @Inject
    lateinit var activeModelProvider: ActiveModelProviderPort

    @Inject
    lateinit var inferenceEventBus: InferenceEventBus

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentJob: Job? = null
    private var currentChatId: String? = null
    private var isRunning = false

    // Notification channel IDs
    private val channelId = "crew_inference_channel"
    private val completionChannelId = "crew_completion_channel"
    private val notificationId = 2001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Service is not meant to be bound
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

        // If already running, cancel previous job
        currentJob?.cancel()

        // Create foreground notification with specialUse type
        val cancelIntent = createCancelPendingIntent()
        val notification = createProgressNotification(
            currentStep = PipelineStep.DRAFT_ONE,
            hasMoreSteps = true,
            cancelPendingIntent = cancelIntent
        )

        startForeground(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        isRunning = true
        currentChatId = chatId

        // Parse state or create initial
        val state = try {
            stateJson?.let { PipelineState.fromJson(it) }
                ?: PipelineState.createInitial(chatId, userMessage)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to parse state: ${e.message}")
            PipelineState.createInitial(chatId, userMessage)
        }

        // Execute pipeline in coroutine
        currentJob = serviceScope.launch {
            try {
                executePipeline(chatId, userMessage, state)
            } catch (e: CancellationException) {
                logger.info(TAG, "Pipeline cancelled for chat: $chatId")
            } catch (e: Exception) {
                logger.error(TAG, "Pipeline error for chat $chatId: ${e.message}", e)
                inferenceEventBus.emitPipelineState(
                    chatId,
                    MessageGenerationState.Failed(
                        IllegalStateException(e.message ?: "Unknown error"),
                        getModelTypeForStep(state.currentStep)
                    )
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

        // Parse saved state
        val savedState = try {
            PipelineState.fromJson(stateJson)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to parse saved state: ${e.message}")
            stopSelf()
            return
        }

        // If already running, cancel previous job
        currentJob?.cancel()

        // Create foreground notification with specialUse type
        val cancelIntent = createCancelPendingIntent()
        val currentStep = savedState.currentStep
        val hasMoreSteps = currentStep.next() != null
        val notification = createProgressNotification(
            currentStep = currentStep,
            hasMoreSteps = hasMoreSteps,
            cancelPendingIntent = cancelIntent
        )

        startForeground(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        isRunning = true
        currentChatId = chatId

        // Execute pipeline from saved state
        currentJob = serviceScope.launch {
            try {
                // Resume from saved state (currentStep is already set in savedState)
                executePipeline(chatId, savedState.userMessage, savedState)
            } catch (e: CancellationException) {
                logger.info(TAG, "Pipeline cancelled for chat: $chatId")
            } catch (e: Exception) {
                logger.error(TAG, "Pipeline error for chat $chatId: ${e.message}", e)
                inferenceEventBus.emitPipelineState(
                    chatId,
                    MessageGenerationState.Failed(
                        IllegalStateException(e.message ?: "Unknown error"),
                        getModelTypeForStep(savedState.currentStep)
                    )
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
        // Clear the pipeline stream so InferenceEventBus doesn't retain an orphaned entry.
        // The executor's onCompletion block also clears as a safety net, but onDestroy
        // covers the case where the service is killed without the coroutine's finally
        // block running first.
        currentChatId?.let { inferenceEventBus.clearPipelineRequest(it) }
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Executes all pipeline steps sequentially.
     */
    private suspend fun executePipeline(
        chatId: String,
        userMessage: String,
        initialState: PipelineState
    ) {
        // Make state mutable so we can update it as we progress through steps
        var currentState = initialState
        var currentStep = currentState.currentStep
        var stepOrder = 0

        while (true) {
            logger.info(TAG, "Executing step: ${currentStep.name}")
            val prompt = buildPromptForStep(currentState)
            val modelType = getModelTypeForStep(currentStep)

            // Emit step started BEFORE model begins generation
            // This ensures ProcessingIndicator appears immediately in UI
            inferenceEventBus.emitPipelineState(
                chatId,
                MessageGenerationState.Processing(modelType)
            )

            val maxTokensOverride = calculateMaxTokensForStep(currentStep)
            val result = executeStepForPipeline(chatId, prompt, currentStep, maxTokensOverride)

            logger.info(TAG, "${currentStep.name} Response: ${result.output}")

            // Store output
            currentState = currentState.withStepOutput(currentStep, result.output)

            // Emit step completion SECOND for ALL steps (including non-FINAL)
            inferenceEventBus.emitPipelineState(
                chatId,
                MessageGenerationState.StepCompleted(
                    stepOutput = "",
                    modelDisplayName = getModelDisplayNameForStep(currentStep),
                    modelType = getModelTypeForStep(currentStep),
                    stepType = currentStep
                )
            )

            // Check if this was the final step
            if (currentStep == PipelineStep.FINAL) {
                logger.info(TAG, "Pipeline complete for chat: $chatId")
                showCompletionNotification(currentState.durationSeconds())
                break
            }

            // Move to next step BEFORE updating notification
            // This ensures notification shows "Draft Two - Next: Synthesis" not "Draft One - Next: Draft Two"
            currentState = currentState.withNextStep() ?: break
            currentStep = currentState.currentStep
            stepOrder++

            // Update notification for the NEXT step
            val hasMoreSteps = currentStep.next() != null
            updateNotification(currentStep, hasMoreSteps)
        }
    }

    /**
     * Executes a single step for the pipeline (closes session after each step to free memory).
     */
    private suspend fun executeStepForPipeline(
        chatId: String,
        prompt: String,
        step: PipelineStep,
        maxTokensOverride: Int? = null
    ): StepResult {
        var output = ""
        val modelType = getModelTypeForStep(step)

        inferenceFactoryProvider.get().withInferenceService(modelType) { service ->
            // Fetch configuration to determine thinking/reasoning budget for this specific step
            val config = activeModelProvider.getActiveConfiguration(modelType)
            
            val currentMaxTokens = config?.maxTokens ?: 1024
            val finalMaxTokens = if (maxTokensOverride != null) {
                kotlin.math.min(maxTokensOverride, currentMaxTokens)
            } else {
                currentMaxTokens
            }

            val hasImageTool = prompt.contains(ToolDefinition.ATTACHED_IMAGE_INSPECT.name)
            val options = GenerationOptions(
                reasoningBudget = if (config?.thinkingEnabled == true) 1024 else 0,
                modelType = modelType,
                maxTokens = finalMaxTokens,
                toolingEnabled = hasImageTool,
                availableTools = if (hasImageTool) {
                    listOf(ToolDefinition.ATTACHED_IMAGE_INSPECT)
                } else {
                    emptyList()
                },
                chatId = ChatId(chatId),
            )

            service.sendPrompt(prompt, options = options, closeConversation = true).collect { event ->
                when (event) {
                    is InferenceEvent.EngineLoading -> Unit
                    is InferenceEvent.Processing -> Unit
                    is InferenceEvent.Thinking -> {
                        inferenceEventBus.tryEmitPipelineState(
                            chatId,
                            MessageGenerationState.ThinkingLive(event.chunk, getModelTypeForStep(step))
                        )
                    }
                    is InferenceEvent.PartialResponse -> {
                        output += event.chunk
                        inferenceEventBus.tryEmitPipelineState(
                            chatId,
                            MessageGenerationState.GeneratingText(event.chunk, getModelTypeForStep(step))
                        )
                    }
                    is InferenceEvent.TavilyResults -> Unit
                    is InferenceEvent.Artifacts -> Unit
                    is InferenceEvent.Finished -> {                        // Ignore. StepCompleted signifies completion for pipeline steps & is detected by flow finishing
                    }
                    is InferenceEvent.SafetyBlocked -> {
                        throw SecurityException("Content blocked: ${event.reason}")
                    }
                    is InferenceEvent.Error -> {
                        throw event.cause
                    }
                }
            }
        }

        return StepResult(output = output)
    }

    /**
     * Builds the prompt for the given step, incorporating previous outputs.
     */
    private suspend fun buildPromptForStep(state: PipelineState): String {
        val userPrompt = state.userMessage
        val modelType = getModelTypeForStep(state.currentStep)
        val config = activeModelProvider.getActiveConfiguration(modelType)
        val contextWindow = config?.contextWindow ?: 4096

        return when (state.currentStep) {
            PipelineStep.DRAFT_ONE -> buildAnalyticalDraftPrompt(userPrompt, config?.systemPrompt ?: "", contextWindow)
            PipelineStep.DRAFT_TWO -> buildCreativeDraftPrompt(userPrompt, config?.systemPrompt ?: "", contextWindow)
            PipelineStep.SYNTHESIS -> buildMainSynthesisPrompt(
                userPrompt,
                state.stepOutputs[PipelineStep.DRAFT_ONE] ?: "",
                state.stepOutputs[PipelineStep.DRAFT_TWO] ?: "",
                config?.systemPrompt ?: "",
                contextWindow
            )
            PipelineStep.FINAL -> {
                val userSystemPrompt = activeModelProvider.getActiveConfiguration(ModelType.FAST)?.systemPrompt ?: ""
                buildFinalReviewPrompt(
                    userPrompt,
                    state.stepOutputs[PipelineStep.SYNTHESIS] ?: "",
                    userSystemPrompt,
                    config?.systemPrompt ?: "",
                    contextWindow
                )
            }
        }
    }

    private suspend fun calculateMaxTokensForStep(step: PipelineStep): Int? {
        return when (step) {
            PipelineStep.DRAFT_ONE, PipelineStep.DRAFT_TWO -> {
                val synthConfig = activeModelProvider.getActiveConfiguration(getModelTypeForStep(PipelineStep.SYNTHESIS))
                val synthContext = synthConfig?.contextWindow ?: 4096
                val synthSystemTokens = (synthConfig?.systemPrompt?.length ?: 0) / 3.5
                val available = (synthContext * 0.75) - synthSystemTokens
                (available * 0.40).toInt().coerceAtLeast(100)
            }
            PipelineStep.SYNTHESIS -> {
                val finalConfig = activeModelProvider.getActiveConfiguration(getModelTypeForStep(PipelineStep.FINAL))
                val finalContext = finalConfig?.contextWindow ?: 4096
                val finalSystemTokens = (finalConfig?.systemPrompt?.length ?: 0) / 3.5
                val available = (finalContext * 0.75) - finalSystemTokens
                (available * 0.74).toInt().coerceAtLeast(100)
            }
            PipelineStep.FINAL -> null // Use default maxTokens from its own config
        }
    }

    private fun truncateChars(text: String, maxTokens: Int): String {
        // Conservative heuristic: 3.5 chars per token for English text
        val maxChars = (maxTokens * 3.5).toInt()
        if (text.length <= maxChars) return text
        return text.take(maxChars) + "\n... [truncated to fit context window]"
    }

    private fun buildAnalyticalDraftPrompt(userPrompt: String, systemPrompt: String, contextWindow: Int): String {
        // Simple draft budget: Reserve 20% for completion + system prompt, 80% for user prompt
        val systemPromptTokens = (systemPrompt.length / 3.5).toInt()
        val budget = (contextWindow * 0.80).toInt() - systemPromptTokens
        val safeUserPrompt = truncateChars(userPrompt, budget.coerceAtLeast(100))

        return """
TASK: COMPLEX_DRAFT_ANALYTICAL

USER_PROMPT:
$safeUserPrompt
""".trimIndent()
    }

    private fun buildCreativeDraftPrompt(userPrompt: String, systemPrompt: String, contextWindow: Int): String {
        // Simple draft budget: Reserve 20% for completion + system prompt, 80% for user prompt
        val systemPromptTokens = (systemPrompt.length / 3.5).toInt()
        val budget = (contextWindow * 0.80).toInt() - systemPromptTokens
        val safeUserPrompt = truncateChars(userPrompt, budget.coerceAtLeast(100))

        return """
TASK: COMPLEX_DRAFT_CREATIVE

USER_PROMPT:
$safeUserPrompt
""".trimIndent()
    }

    private fun buildMainSynthesisPrompt(userPrompt: String, draft1: String, draft2: String, modelSystemPrompt: String, contextWindow: Int): String {
        // Synthesis model budget strategy:
        // Reserve 25% for completion + native model system prompt + overhead
        // Divide remaining 75% as: 15% user prompt, 30% per draft
        val systemPromptTokens = (modelSystemPrompt.length / 3.5).toInt()
        val totalAvailable = (contextWindow * 0.75).toInt() - systemPromptTokens
        
        val draftBudget = (totalAvailable * 0.40).toInt() // 40% of available (30% of total) per draft
        val promptBudget = (totalAvailable * 0.20).toInt() // 20% of available (15% of total) for user prompt

        val safeUserPrompt = truncateChars(userPrompt, promptBudget.coerceAtLeast(100))
        val safeDraft1 = truncateChars(draft1, draftBudget.coerceAtLeast(100))
        val safeDraft2 = truncateChars(draft2, draftBudget.coerceAtLeast(100))

        return """
TASK: COMPLEX_SYNTHESIZE

ORIGINAL_USER_PROMPT:
$safeUserPrompt

DRAFT_1:
$safeDraft1

DRAFT_2:
$safeDraft2
""".trimIndent()
    }

    private fun buildFinalReviewPrompt(userPrompt: String, candidateAnswer: String, userSystemPrompt: String, modelSystemPrompt: String, contextWindow: Int): String {
        // Final Review budget strategy:
        // Reserve 25% for completion + native model system prompt + overhead
        // Divide remaining 75% as: 10% user prompt, 10% user custom system prompt, 55% synthesis candidate
        val modelSystemPromptTokens = (modelSystemPrompt.length / 3.5).toInt()
        val totalAvailable = (contextWindow * 0.75).toInt() - modelSystemPromptTokens
        
        val userPromptBudget = (totalAvailable * 0.13).toInt() // ~10% of total
        val userSystemBudget = (totalAvailable * 0.13).toInt() // ~10% of total
        val candidateBudget = (totalAvailable * 0.74).toInt() // ~55% of total (scaled to available)

        val safeUserPrompt = truncateChars(userPrompt, userPromptBudget.coerceAtLeast(100))
        val safeCandidate = truncateChars(candidateAnswer, candidateBudget.coerceAtLeast(100))
        val safeUserSystemPrompt = truncateChars(userSystemPrompt, userSystemBudget.coerceAtLeast(100))

        return """
TASK: FINAL_REVIEW_AND_REPLY

ORIGINAL_USER_PROMPT:
$safeUserPrompt

CANDIDATE_ANSWER:
$safeCandidate

USER_SYSTEM_PROMPT:
${safeUserSystemPrompt.ifEmpty { "(none provided)" }}

OUTPUT_CONTRACT:
Produce the final polished response for the user. Output ONLY the essay itself — no critique, no feedback, no suggestions, no headings. Just the clean final answer.
""".trimIndent()
    }

    /**
     * Maps a PipelineStep to its corresponding ModelType.
     */
    private fun getModelTypeForStep(step: PipelineStep): ModelType {
        return when (step) {
            PipelineStep.DRAFT_ONE -> ModelType.DRAFT_ONE
            PipelineStep.DRAFT_TWO -> ModelType.DRAFT_TWO
            PipelineStep.SYNTHESIS -> ModelType.MAIN
            PipelineStep.FINAL -> ModelType.FINAL_SYNTHESIS
        }
    }

    /**
     * Gets the display name for a PipelineStep from the ModelRegistry.
     */
    private suspend fun getModelDisplayNameForStep(step: PipelineStep): String {
        val modelType = getModelTypeForStep(step)
        return activeModelProvider.getActiveConfiguration(modelType)?.name ?: modelType.name
    }

    private fun createNotificationChannel() {
        // Progress channel
        val progressChannel = NotificationChannel(
            channelId,
            "Crew Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress when Crew is thinking"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(progressChannel)

        // Completion channel
        val completionChannel = NotificationChannel(
            completionChannelId,
            "Crew Complete",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Shows when Crew finishes thinking"
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(completionChannel)
    }

    private fun createCancelPendingIntent(): PendingIntent {
        val cancelIntent = Intent(this, InferenceService::class.java).apply {
            action = ACTION_STOP
        }
        return PendingIntent.getService(
            this,
            0,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createProgressNotification(
        currentStep: PipelineStep,
        hasMoreSteps: Boolean,
        cancelPendingIntent: PendingIntent
    ): Notification {
        val progress = when (currentStep) {
            PipelineStep.DRAFT_ONE -> 25
            PipelineStep.DRAFT_TWO -> 50
            PipelineStep.SYNTHESIS -> 75
            PipelineStep.FINAL -> 100
        }

        val contentTitle = "Crew is thinking"
        val contentText = currentStep.displayName()
        val nextStepText = if (hasMoreSteps) {
            "Next: ${currentStep.next()?.displayName() ?: "Complete"}"
        } else {
            "Final step..."
        }

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        } ?: Intent(packageName).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(contentTitle)
            .setContentText("$contentText - $nextStepText")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPendingIntent
            )
            .build()
    }

    private fun updateNotification(currentStep: PipelineStep, hasMoreSteps: Boolean) {
        val cancelIntent = createCancelPendingIntent()
        val notification = createProgressNotification(
            currentStep = currentStep,
            hasMoreSteps = hasMoreSteps,
            cancelPendingIntent = cancelIntent
        )
        notificationManager.notify(notificationId, notification)
    }

    private fun showCompletionNotification(thinkingDurationSeconds: Int) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        } ?: Intent(packageName).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, completionChannelId)
            .setContentTitle("Crew finished thinking")
            .setContentText("Completed in ${thinkingDurationSeconds}s - tap to see response")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .build()

        notificationManager.notify(notificationId + 1, notification)
    }

    /**
     * Result of executing a single step.
     */
    private data class StepResult(val output: String)
}
