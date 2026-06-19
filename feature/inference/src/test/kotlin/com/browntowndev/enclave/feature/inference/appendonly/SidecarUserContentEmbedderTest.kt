package com.browntowndev.enclave.feature.inference.appendonly

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SidecarUserContentEmbedderTest {
    private val uuidA = "11111111-2222-3333-4444-555555555555"
    private val uuidB = "66666666-7777-8888-9999-aaaaaaaaaaaa"

    @Test
    fun S_EMBED_01_wireBlock_produces_UUID_stamped_form() {
        val marker = SidecarMarker(markerId = uuidA, body = """State ledger JSON: {"v":1}""")
        assertEquals(
            "[Sidecar-11111111-2222-3333-4444-555555555555: State ledger JSON: {\"v\":1}]",
            marker.wireBlock(),
        )
    }

    @Test
    fun S_EMBED_02_embed_appends_single_marker_after_base_content() {
        val marker = SidecarMarker(markerId = uuidA, body = "ledger")
        assertEquals(
            "Hello\n\n[Sidecar-11111111-2222-3333-4444-555555555555: ledger]",
            SidecarUserContentEmbedder.embed("Hello", listOf(marker)),
        )
    }

    @Test
    fun S_EMBED_03_embed_appends_multiple_markers_in_order() {
        val markers = listOf(
            SidecarMarker(markerId = uuidA, body = "ledger-A"),
            SidecarMarker(markerId = uuidB, body = "ledger-B"),
        )
        assertEquals(
            "Hello\n\n[Sidecar-11111111-2222-3333-4444-555555555555: ledger-A]" +
                "\n\n[Sidecar-66666666-7777-8888-9999-aaaaaaaaaaaa: ledger-B]",
            SidecarUserContentEmbedder.embed("Hello", markers),
        )
    }

    @Test
    fun S_EMBED_04_embed_preserves_metadata_suffix_before_sidecar() {
        val base = "Hello\n\n[Metadata: Sent at Thursday, June 18, 2026 at 3:45 PM]"
        val marker = SidecarMarker(markerId = uuidA, body = "ledger")
        val result = SidecarUserContentEmbedder.embed(base, listOf(marker))
        assertTrue(result.startsWith(base))
        assertTrue(result.endsWith("\n\n[Sidecar-11111111-2222-3333-4444-555555555555: ledger]"))
        val metadataIdx = result.indexOf("[Metadata:")
        val sidecarIdx = result.indexOf("[Sidecar-")
        assertTrue(metadataIdx in 0 until sidecarIdx)
    }

    @Test
    fun S_EMBED_05_stripSidecarBlocks_removes_only_UUID_form_blocks() {
        val content = "Hello\n\n[Metadata: Sent at ...]\n\n" +
            "[Sidecar-11111111-2222-3333-4444-555555555555: ledger]\n\n" +
            "[Sidecar:user-typed-not-a-uuid: fake]"
        assertEquals(
            "Hello\n\n[Metadata: Sent at ...]\n\n[Sidecar:user-typed-not-a-uuid: fake]",
            SidecarUserContentEmbedder.stripSidecarBlocks(content),
        )
    }

    @Test
    fun S_EMBED_06_SidecarMarkerRegex_matches_only_UUID_form() {
        val uuidForm = "[Sidecar-11111111-2222-3333-4444-555555555555:"
        val allHyphens = "[Sidecar-${"-".repeat(36)}:"
        val wrongGrouping = "[Sidecar-111111111-222-3333-4444-555555555555:"
        val nonUuidColon = "[Sidecar:not-a-uuid:"
        val bare = "[Sidecar:"
        val metadata = "[Metadata: Sent at ..."
        assertTrue(SidecarMarkerRegex.SPAN.containsMatchIn(uuidForm))
        assertFalse(SidecarMarkerRegex.SPAN.containsMatchIn(allHyphens))
        assertFalse(SidecarMarkerRegex.SPAN.containsMatchIn(wrongGrouping))
        assertFalse(SidecarMarkerRegex.SPAN.containsMatchIn(nonUuidColon))
        assertFalse(SidecarMarkerRegex.SPAN.containsMatchIn(bare))
        assertFalse(SidecarMarkerRegex.SPAN.containsMatchIn(metadata))
    }

    @Test
    fun S_MUT_01_stripSidecarBlocks_must_not_strip_bare_Sidecar_without_UUID() {
        val content = "Hello\n\n[Sidecar:user note]\n\n" +
            "[Sidecar-11111111-2222-3333-4444-555555555555: ledger]"
        val result = SidecarUserContentEmbedder.stripSidecarBlocks(content)
        assertTrue(result.contains("[Sidecar:user note]"))
        assertFalse(result.contains("[Sidecar-11111111-2222-3333-4444-555555555555: ledger]"))
    }

    @Test
    fun S_MUT_02_stripSidecarBlocks_removes_outer_block_with_nested_UUID_marker_like_text() {
        val content = "Hello\n\n[Sidecar-$uuidA: outer [Sidecar-$uuidB: inner] body]\n\nAfter"

        assertEquals(
            "Hello\n\nAfter",
            SidecarUserContentEmbedder.stripSidecarBlocks(content),
        )
    }

    @Test
    fun S_MUT_03_stripSidecarBlocks_handles_square_brackets_in_sidecar_body() {
        val content = "Hello\n\n" +
            "[Sidecar-$uuidA: {\"items\":[\"a\",\"b\"],\"nested\":[{\"x\":[1,2]}]}]\n\n" +
            "After"

        assertEquals(
            "Hello\n\nAfter",
            SidecarUserContentEmbedder.stripSidecarBlocks(content),
        )
    }
}