package com.browntowndev.pocketcrew.core.data.artifact

import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactSection
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactBlock
import com.browntowndev.pocketcrew.domain.model.artifact.DocumentType
import com.browntowndev.pocketcrew.domain.model.inference.GenerateArtifactParams
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ArtifactToolExecutor Implementation")
class ArtifactToolExecutorImplTest {

    private val executor = ArtifactToolExecutor()

    @Test
    @DisplayName("Successfully executes PDF request")
    fun `executes PDF request`() = runTest {
        val params = GenerateArtifactParams(
            documentType = DocumentType.PDF,
            title = "Test PDF",
            sections = listOf(
                ArtifactSection(
                    title = "Section 1",
                    blocks = listOf(ArtifactBlock.Paragraph(text = "Test text"))
                )
            )
        )

        val result = executor.execute(params)

        assertTrue(result.resultJson.contains("success"))
        assertTrue(result.resultJson.contains("PDF"))
    }

    @Test
    @DisplayName("Rejects WORD request in Phase 1")
    fun `rejects WORD request`() = runTest {
        val params = GenerateArtifactParams(
            documentType = DocumentType.WORD,
            title = "Test Word",
            sections = emptyList()
        )

        val result = runCatching { executor.execute(params) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(result.exceptionOrNull()?.message?.contains("not supported in Phase 1") == true)
    }

    @Test
    @DisplayName("Rejects wrong tool name")
    fun `rejects wrong tool name`() = runTest {
        val request = ToolCallRequest(
            toolName = "wrong_tool",
            argumentsJson = "{}",
            provider = "test",
            modelType = ModelType.MAIN
        )

        val result = runCatching { executor.execute(request) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertTrue(result.exceptionOrNull()?.message?.contains("Unsupported tool") == true)
    }
}
