package com.browntowndev.pocketcrew.core.data.artifact

import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.domain.model.inference.GenerateArtifactParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.ArtifactToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of the artifact tool executor.
 * Phase 1 only supports PDF. Other document types will be routed to a server in future phases.
 */
@Singleton
class ArtifactToolExecutor @Inject constructor() : ArtifactToolExecutorPort, ToolExecutorPort {

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        require(request.toolName == ToolDefinition.GENERATE_ARTIFACT.name) {
            "Unsupported tool: ${request.toolName}"
        }
        val params = request.parameters as GenerateArtifactParams
        return execute(params)
    }

    override suspend fun execute(params: GenerateArtifactParams): ToolExecutionResult {
        val targetFormat = params.targetFormat

        if (targetFormat != MimeType.PDF) {
            throw IllegalArgumentException(
                "Document type ${params.targetFormat} is not supported in Phase 1. " +
                "Only PDF is available on-device. Other formats (Word, Excel, PowerPoint) will be supported via server in a future update."
            )
        }

        // Validate the content by attempting to convert to a request.
        val artifactRequest = params.toRequest()
        
        if (artifactRequest.content.isEmpty()) {
            return ToolExecutionResult(
                toolName = "generate_artifact",
                resultJson = "{\"status\":\"error\"," +
                    "\"message\":\"Failed to parse artifact markdown. " +
                    "Please ensure you are providing content in standard Markdown format.\"}",
                cached = false,
                latencyMs = 20
            )
        }

        return ToolExecutionResult(
            toolName = "generate_artifact",
            resultJson = buildString {
                append("{")
                append("\"status\":\"success\",")
                append("\"document_type\":\"PDF\",")
                append("\"title\":\"${params.title}\",")
                append("\"content_length\":${artifactRequest.content.length},")
                append("\"message\":\"Artifact Markdown validated. PDF rendering will be triggered by the UI.\"")
                append("}")
            },
            cached = false,
            latencyMs = 50
        )
    }
}
