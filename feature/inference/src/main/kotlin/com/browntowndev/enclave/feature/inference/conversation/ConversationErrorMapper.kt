package com.browntowndev.enclave.feature.inference.conversation

import java.lang.reflect.InvocationTargetException

internal sealed class ConversationToolError {
    abstract val toolName: String

    data class ExecutionFailed(
        override val toolName: String,
        val exceptionType: String,
        val message: String,
    ) : ConversationToolError()

    data class ContextWindowExceeded(
        override val toolName: String,
    ) : ConversationToolError()
}

internal object ConversationErrorMapper {
    fun toJson(error: ConversationToolError): String =
        when (error) {
            is ConversationToolError.ExecutionFailed ->
                """{"error":"tool_execution_failed","tool":"${escapeJson(
                    error.toolName,
                )}","exception":"${escapeJson(error.exceptionType)}","message":"${escapeJson(error.message)}"}"""
            is ConversationToolError.ContextWindowExceeded ->
                LocalToolResultPolicyBridge.buildContextWindowExceededToolError(error.toolName)
        }

    fun executionFailed(
        toolName: String,
        throwable: Throwable,
    ): String =
        toJson(
            ConversationToolError.ExecutionFailed(
                toolName = toolName,
                exceptionType = throwable::class.java.simpleName,
                message = throwable.message ?: "Unknown error",
            ),
        )

    fun rootCause(throwable: Throwable): Throwable {
        val cause =
            when (throwable) {
                is InvocationTargetException -> throwable.targetException ?: throwable.cause
                else -> throwable.cause
            }
        return if (cause == null || cause === throwable) throwable else rootCause(cause)
    }

    fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
}

/** Avoids a circular dependency on LocalToolResultPolicy from the conversation package. */
private object LocalToolResultPolicyBridge {
    fun buildContextWindowExceededToolError(toolName: String): String =
        com.browntowndev.enclave.feature.inference.LocalToolResultPolicy
            .buildContextWindowExceededToolError(toolName)
}
