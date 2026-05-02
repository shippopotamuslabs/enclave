package com.browntowndev.pocketcrew.core.data.artifact

import com.browntowndev.pocketcrew.domain.model.artifact.DocumentType
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
        val documentType = params.documentType

        if (documentType != DocumentType.PDF) {
            throw IllegalArgumentException(
                "Document type ${params.documentType} is not supported in Phase 1. " +
                "Only PDF is available on-device. Other formats (Word, Excel, PowerPoint) will be supported via server in a future update."
            )
        }

        // Validate the content by attempting to convert to a request.
        val artifactRequest = params.toRequest()
        
        if (artifactRequest.sections.isEmpty()) {
            return ToolExecutionResult(
                toolName = "generate_artifact",
                resultJson = "{\"status\":\"error\",\"message\":\"Failed to parse artifact sections. Please ensure you are providing a valid list of sections with appropriate blocks (heading, paragraph, bullet_list, numbered_list, table, code_block).\"}",
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
                append("\"sections_count\":${artifactRequest.sections.size},")
                append("\"message\":\"Artifact JSON validated. PDF rendering will be triggered by the UI.\"")
                append("}")
            },
            cached = false,
            latencyMs = 50
        )
    }
}
