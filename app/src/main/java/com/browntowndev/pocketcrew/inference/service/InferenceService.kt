package com.browntowndev.pocketcrew.inference.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.browntowndev.pocketcrew.app.DraftOneModelEngine
import com.browntowndev.pocketcrew.app.DraftTwoModelEngine
import com.browntowndev.pocketcrew.app.FinalSynthesizerModelEngine
import com.browntowndev.pocketcrew.app.MainModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineState
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.BufferThinkingStepsUseCase
import com.browntowndev.pocketcrew.presentation.MainActivity
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
 * Custom foreground Service for executing the Crew Mode pipeline.
 * Uses `specialUse` foreground service type which has no quota limits
 * (unlike dataSync which has a 6-hour limit in any 24-hour period on Android 15+).
 *
 * This service:
 * 1. Runs as a foreground service for the entire pipeline (persistent notification)
 * 2. Executes all 4 steps: DRAFT_ONE -> DRAFT_TWO -> SYNTHESIS -> FINAL
 * 3. Emits progress via LocalBroadcastManager for real-time UI updates
 * 4. Handles cancellation via notification action
 */
@AndroidEntryPoint
class InferenceService : Service() {

    companion object {
        const val TAG = "InferenceService"
        const val ACTION_START = "com.browntowndev.pocketcrew.inference.ACTION_START"
        const val ACTION_STOP = "com.browntowndev.pocketcrew.inference.ACTION_STOP"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_USER_MESSAGE = "user_message"
        const val EXTRA_STATE_JSON = "state_json"

        // Broadcast actions for progress updates
        const val BROADCAST_PROGRESS = "com.browntowndev.pocketcrew.inference.BROADCAST_PROGRESS"
        const val BROADCAST_COMPLETE = "com.browntowndev.pocketcrew.inference.BROADCAST_COMPLETE"
        const val BROADCAST_ERROR = "com.browntowndev.pocketcrew.inference.BROADCAST_ERROR"
        const val BROADCAST_STEP_COMPLETED = "com.browntowndev.pocketcrew.inference.BROADCAST_STEP_COMPLETED"

        // Intent extras for broadcasts
        const val EXTRA_THINKING_CHUNK = "thinking_chunk"
        const val EXTRA_THINKING_STEP = "thinking_step"
        const val EXTRA_STEP_OUTPUT = "step_output"
        const val EXTRA_MODEL_TYPE = "model_type"
        const val EXTRA_FINAL_RESPONSE = "final_response"
        const val EXTRA_ERROR_MESSAGE = "error_message"

        // Step completion extras
        const val EXTRA_STEP_NAME = "step_name"
        const val EXTRA_STEP_DURATION = "step_duration"
        const val EXTRA_STEP_TOTAL_DURATION = "step_total_duration"
        const val EXTRA_STEP_THINKING_STEPS = "step_thinking_steps"
        const val EXTRA_STEP_MODEL_DISPLAY_NAME = "step_model_display_name"
        const val EXTRA_STEP_TYPE = "step_type"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_IS_FINAL_STEP = "is_final_step"
    }

    @Inject
    lateinit var notificationManager: NotificationManager

    @Inject
    lateinit var logger: LoggingPort

    @Inject
    @field:DraftOneModelEngine
    lateinit var draftOneServiceProvider: dagger.Lazy<LlmInferencePort>

    @Inject
    @field:DraftTwoModelEngine
    lateinit var draftTwoServiceProvider: dagger.Lazy<LlmInferencePort>

    @Inject
    @field:MainModelEngine
    lateinit var mainServiceProvider: dagger.Lazy<LlmInferencePort>

    @Inject
    @field:FinalSynthesizerModelEngine
    lateinit var finalSynthesizerServiceProvider: dagger.Lazy<LlmInferencePort>

    // Lambdas for service access (matching worker pattern)
    private lateinit var draftOneService: () -> LlmInferencePort
    private lateinit var draftTwoService: () -> LlmInferencePort
    private lateinit var synthesisService: () -> LlmInferencePort
    private lateinit var finalService: () -> LlmInferencePort

    @Inject
    lateinit var modelRegistry: ModelRegistryPort

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentJob: Job? = null
    private var isRunning = false

    // Notification channel IDs
    private val channelId = "crew_inference_channel"
    private val completionChannelId = "crew_completion_channel"
    private val notificationId = 2001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Initialize service lambdas (matching worker pattern)
        draftOneService = { draftOneServiceProvider.get() }
        draftTwoService = { draftTwoServiceProvider.get() }
        synthesisService = { mainServiceProvider.get() }
        finalService = { finalSynthesizerServiceProvider.get() }
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Service is not meant to be bound
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStartCommand(intent)
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

        // Start foreground with specialUse type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(notificationId, notification)
        }

        isRunning = true

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
                broadcastProgress(EXTRA_THINKING_STEP, "Cancelled")
            } catch (e: Exception) {
                logger.error(TAG, "Pipeline error for chat $chatId: ${e.message}", e)
                broadcastError(e.message ?: "Unknown error")
            } finally {
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

    override fun onDestroy() {
        currentJob?.cancel()
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
        // Get all services upfront (as Lazy to avoid loading until needed)
        val draftOneServiceProvider = { draftOneServiceProvider.get() }
        // Map steps to services using class-level lambdas
        val stepServices = mapOf(
            PipelineStep.DRAFT_ONE to draftOneService,
            PipelineStep.DRAFT_TWO to draftTwoService,
            PipelineStep.SYNTHESIS to synthesisService,
            PipelineStep.FINAL to finalService
        )

        // Make state mutable so we can update it as we progress through steps
        var currentState = initialState
        var currentStep = currentState.currentStep
        var stepOrder = 0

        while (true) {
            logger.info(TAG, "Executing step: ${currentStep.name}")

            val serviceGetter = stepServices[currentStep]
                ?: throw IllegalStateException("No service for step: $currentStep")

            val service = serviceGetter()
                ?: throw IllegalStateException("Service not available for step: $currentStep")

            val stepStartTime = System.currentTimeMillis()
            val prompt = buildPromptForStep(currentState)
            val result = executeStepForPipeline(service, prompt, currentStep) { _ ->
                // Callback - timing now handled in PipelineExecutor
            }
            val stepDuration = ((System.currentTimeMillis() - stepStartTime) / 1000).toInt()

            logger.info(TAG, "${currentStep.name} Response: ${result.output}, totalDuration=${stepDuration}s")

            // Store output
            currentState = currentState.withStepOutput(currentStep, result.output)

            // Broadcast step output FIRST - for all steps (to trigger GENERATING state in ViewModel)
            // For FINAL step, this also streams the text to the UI
            val isFinalStep = currentStep == PipelineStep.FINAL
            val modelType = getModelTypeForStep(currentStep)
            broadcastComplete(result.output, isFinalStep, modelType)

            // Broadcast step completion SECOND for ALL steps (including non-FINAL)
            // This must come AFTER broadcastComplete so that StepCompleted sets responseState to PROCESSING
            // after GeneratingText has been processed
            // Note: thinkingDurationSeconds is calculated in PipelineExecutor from actual timing
            broadcastStepCompleted(
                stepName = currentStep.displayName(),
                stepOutput = result.output,
                thinkingDurationSeconds = 0,  // Calculated in PipelineExecutor
                totalDurationSeconds = stepDuration,  // Total time (for BottomSheet)
                modelDisplayName = getModelDisplayNameForStep(currentStep),
                modelType = getModelTypeForStep(currentStep),
                stepType = currentStep
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
     * @param onFirstPartialResponse Callback invoked when first visible text arrives (thinking ends)
     */
    private suspend fun executeStepForPipeline(
        service: LlmInferencePort,
        prompt: String,
        step: PipelineStep,
        onFirstPartialResponse: (Boolean) -> Unit = {}
    ): StepResult {
        var output = ""
        var isFirstPartialResponse = true

        try {
            service.sendPrompt(prompt, closeConversation = false).collect { event ->
                when (event) {
                    is InferenceEvent.Thinking -> {
                        // Broadcast true thinking from model - NOT the response
                        broadcastProgress(EXTRA_THINKING_CHUNK, event.chunk, getModelTypeForStep(step).name)
                    }
                    is InferenceEvent.PartialResponse -> {
                        // Collect text for final output step
                        if (step == PipelineStep.FINAL) {
                            output += event.chunk
                        }
                        // FIX: Also broadcast partial response for ALL steps so UI shows "Generating..." indicator
                        // For non-FINAL steps, this triggers the GENERATING state in ViewModel
                        broadcastProgress(EXTRA_STEP_OUTPUT, event.chunk, getModelTypeForStep(step).name)
                        // Notify when first visible text arrives (thinking has ended)
                        if (isFirstPartialResponse) {
                            isFirstPartialResponse = false
                            onFirstPartialResponse(true)
                        }
                    }
                    is InferenceEvent.Completed -> {
                        output = event.finalResponse
                        // Don't broadcast thinking here - thinking only comes from InferenceEvent.Thinking events
                    }
                    is InferenceEvent.SafetyBlocked -> {
                        throw SecurityException("Content blocked: ${event.reason}")
                    }
                    is InferenceEvent.Error -> {
                        throw event.cause
                    }
                }
            }
        } finally {
            try {
                service.closeSession()
            } catch (e: Exception) {
                logger.warning(TAG, "Error closing service: ${e.message}")
            }
        }

        return StepResult(output = output)
    }

    /**
     * Broadcasts progress to UI via LocalBroadcastManager.
     */
    private fun broadcastProgress(extraKey: String, value: String, modelType: String? = null) {
        val intent = Intent(BROADCAST_PROGRESS).apply {
            putExtra(extraKey, value)
            modelType?.let { putExtra(EXTRA_MODEL_TYPE, it) }
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    /**
     * Broadcasts completion with final response.
     */
    private fun broadcastComplete(finalResponse: String, isFinalStep: Boolean, modelType: ModelType) {
        val intent = Intent(BROADCAST_COMPLETE).apply {
            putExtra(EXTRA_FINAL_RESPONSE, finalResponse)
            putExtra(EXTRA_IS_FINAL_STEP, isFinalStep)
            putExtra(EXTRA_MODEL_TYPE, modelType.name)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    /**
     * Broadcasts step completion for Crew mode progress display.
     * @param thinkingDurationSeconds Thinking time only (for "Thought For Xs")
     * @param totalDurationSeconds Total step time (thinking + generation, for BottomSheet)
     */
    private fun broadcastStepCompleted(
        stepName: String,
        stepOutput: String,
        thinkingDurationSeconds: Int,
        totalDurationSeconds: Int,
        modelDisplayName: String,
        modelType: ModelType,
        stepType: PipelineStep
    ) {
        val intent = Intent(BROADCAST_STEP_COMPLETED).apply {
            putExtra(EXTRA_STEP_NAME, stepName)
            putExtra(EXTRA_STEP_OUTPUT, stepOutput)
            putExtra(EXTRA_STEP_DURATION, thinkingDurationSeconds)
            putExtra(EXTRA_STEP_TOTAL_DURATION, totalDurationSeconds)
            putExtra(EXTRA_STEP_MODEL_DISPLAY_NAME, modelDisplayName)
            putExtra(EXTRA_MODEL_TYPE, modelType.name)
            putExtra(EXTRA_STEP_TYPE, stepType.name)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    /**
     * Broadcasts error to UI.
     */
    private fun broadcastError(errorMessage: String) {
        val intent = Intent(BROADCAST_ERROR).apply {
            putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    /**
     * Builds the prompt for the given step, incorporating previous outputs.
     */
    private fun buildPromptForStep(state: PipelineState): String {
        val userPrompt = state.userMessage

        return when (state.currentStep) {
            PipelineStep.DRAFT_ONE -> buildAnalyticalDraftPrompt(userPrompt)
            PipelineStep.DRAFT_TWO -> buildCreativeDraftPrompt(userPrompt)
            PipelineStep.SYNTHESIS -> buildMainSynthesisPrompt(
                userPrompt,
                state.stepOutputs[PipelineStep.DRAFT_ONE] ?: "",
                state.stepOutputs[PipelineStep.DRAFT_TWO] ?: ""
            )
            PipelineStep.FINAL -> {
                val userSystemPrompt = modelRegistry.getRegisteredModelSync(ModelType.FAST)?.persona?.systemPrompt ?: ""
                buildFinalReviewPrompt(
                    userPrompt,
                    state.stepOutputs[PipelineStep.SYNTHESIS] ?: "",
                    userSystemPrompt
                )
            }
        }
    }

    private fun buildAnalyticalDraftPrompt(userPrompt: String): String = """
TASK: COMPLEX_DRAFT_ANALYTICAL

USER_PROMPT:
$userPrompt
""".trimIndent()

    private fun buildCreativeDraftPrompt(userPrompt: String): String = """
TASK: COMPLEX_DRAFT_CREATIVE

USER_PROMPT:
$userPrompt
""".trimIndent()

    private fun buildMainSynthesisPrompt(userPrompt: String, draft1: String, draft2: String): String = """
TASK: COMPLEX_SYNTHESIZE

ORIGINAL_USER_PROMPT:
$userPrompt

DRAFT_1:
$draft1

DRAFT_2:
$draft2
""".trimIndent()

    private fun buildFinalReviewPrompt(userPrompt: String, candidateAnswer: String, userSystemPrompt: String): String = """
TASK: FINAL_REVIEW_AND_REPLY

ORIGINAL_USER_PROMPT:
$userPrompt

CANDIDATE_ANSWER:
$candidateAnswer

USER_SYSTEM_PROMPT:
${userSystemPrompt.ifEmpty { "(none provided)" }}

OUTPUT_CONTRACT:
Produce the final polished response for the user. Output ONLY the essay itself — no critique, no feedback, no suggestions, no headings. Just the clean final answer.
""".trimIndent()

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
    private fun getModelDisplayNameForStep(step: PipelineStep): String {
        val modelType = getModelTypeForStep(step)
        return modelRegistry.getRegisteredModelSync(modelType)?.metadata?.displayName ?: modelType.name
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

        val intent = Intent(this, MainActivity::class.java).apply {
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
        val intent = Intent(this, MainActivity::class.java).apply {
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
