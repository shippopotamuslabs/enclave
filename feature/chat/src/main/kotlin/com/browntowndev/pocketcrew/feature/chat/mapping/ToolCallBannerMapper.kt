package com.browntowndev.pocketcrew.feature.chat.mapping

import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.feature.chat.ToolCallBannerKind
import com.browntowndev.pocketcrew.feature.chat.ToolCallBannerUi

/**
 * Maps tool execution names to transient tool-call banner presentation.
 */
object ToolCallBannerMapper {
    private val kindByToolName: Map<String, ToolCallBannerKind> =
        mapOf(
            ToolDefinition.WEB_SEARCH.name to ToolCallBannerKind.SEARCH,
            ToolDefinition.WEB_EXTRACT.name to ToolCallBannerKind.EXTRACT,
            ToolDefinition.ATTACHED_IMAGE_INSPECT.name to ToolCallBannerKind.IMAGE,
            ToolDefinition.SEARCH_CHAT_HISTORY.name to ToolCallBannerKind.MEMORY,
            ToolDefinition.SEARCH_CHAT.name to ToolCallBannerKind.MEMORY,
            ToolDefinition.START_ARTIFACT.name to ToolCallBannerKind.ARTIFACT,
            ToolDefinition.APPEND_BLOCKS.name to ToolCallBannerKind.ARTIFACT,
            ToolDefinition.SEARCH_CURRENT_ARTIFACT.name to ToolCallBannerKind.ARTIFACT,
            ToolDefinition.X_SEARCH.name to ToolCallBannerKind.X_SEARCH,
            ToolDefinition.GENERATE_IMAGE.name to ToolCallBannerKind.MEDIA,
            ToolDefinition.GENERATE_VIDEO.name to ToolCallBannerKind.MEDIA,
            ToolDefinition.GENERATE_MUSIC.name to ToolCallBannerKind.MEDIA,
        )

    private val fallbackLabelByToolName: Map<String, String> =
        mapOf(
            ToolDefinition.WEB_SEARCH.name to "Searching the web",
            ToolDefinition.WEB_EXTRACT.name to "Reading webpage content",
            ToolDefinition.ATTACHED_IMAGE_INSPECT.name to "Inspecting image",
            ToolDefinition.SEARCH_CHAT_HISTORY.name to "Searching chat history",
            ToolDefinition.SEARCH_CHAT.name to "Searching chat",
            ToolDefinition.START_ARTIFACT.name to "Starting Artifact",
            ToolDefinition.APPEND_BLOCKS.name to "Writing Artifact",
            ToolDefinition.SEARCH_CURRENT_ARTIFACT.name to "Searching Artifact",
            ToolDefinition.X_SEARCH.name to "Searching X",
            ToolDefinition.GENERATE_IMAGE.name to "Generating Image",
            ToolDefinition.GENERATE_VIDEO.name to "Generating Video",
            ToolDefinition.GENERATE_MUSIC.name to "Generating Music",
        )

    fun bannerKind(toolName: String): ToolCallBannerKind = kindByToolName[toolName] ?: ToolCallBannerKind.SEARCH

    fun bannerLabel(toolName: String): String {
        val toolDef = ToolDefinition.fromName(toolName)
        return toolDef?.displayName ?: fallbackLabelByToolName[toolName] ?: "Executing tool"
    }

    fun toStartedBanner(toolName: String): ToolCallBannerUi =
        ToolCallBannerUi(
            kind = bannerKind(toolName),
            label = bannerLabel(toolName),
        )

    fun toExtractingBanner(url: String): ToolCallBannerUi =
        ToolCallBannerUi(
            kind = ToolCallBannerKind.EXTRACT,
            label = "Reading $url",
        )
}
