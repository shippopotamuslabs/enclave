package com.browntowndev.pocketcrew.feature.chat.mapping

import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.feature.chat.ToolCallBannerKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ToolCallBannerMapperTest {
    @Test
    fun bannerKind_mapsKnownTools() {
        assertEquals(ToolCallBannerKind.SEARCH, ToolCallBannerMapper.bannerKind(ToolDefinition.WEB_SEARCH.name))
        assertEquals(ToolCallBannerKind.EXTRACT, ToolCallBannerMapper.bannerKind(ToolDefinition.WEB_EXTRACT.name))
        assertEquals(ToolCallBannerKind.ARTIFACT, ToolCallBannerMapper.bannerKind(ToolDefinition.START_ARTIFACT.name))
        assertEquals(ToolCallBannerKind.X_SEARCH, ToolCallBannerMapper.bannerKind(ToolDefinition.X_SEARCH.name))
        assertEquals(ToolCallBannerKind.MEDIA, ToolCallBannerMapper.bannerKind(ToolDefinition.GENERATE_IMAGE.name))
    }

    @Test
    fun bannerKind_unknownToolDefaultsToSearch() {
        assertEquals(ToolCallBannerKind.SEARCH, ToolCallBannerMapper.bannerKind("unknown_tool"))
    }

    @Test
    fun bannerLabel_prefersToolDefinitionDisplayName() {
        assertEquals(
            ToolDefinition.WEB_SEARCH.displayName,
            ToolCallBannerMapper.bannerLabel(ToolDefinition.WEB_SEARCH.name),
        )
    }

    @Test
    fun bannerLabel_unknownToolUsesExecutingToolFallback() {
        assertEquals("Executing tool", ToolCallBannerMapper.bannerLabel("not_a_real_tool"))
    }

    @Test
    fun toStartedBanner_combinesKindAndLabel() {
        val banner = ToolCallBannerMapper.toStartedBanner(ToolDefinition.WEB_SEARCH.name)
        assertEquals(ToolCallBannerKind.SEARCH, banner.kind)
        assertEquals(ToolDefinition.WEB_SEARCH.displayName, banner.label)
    }
}
