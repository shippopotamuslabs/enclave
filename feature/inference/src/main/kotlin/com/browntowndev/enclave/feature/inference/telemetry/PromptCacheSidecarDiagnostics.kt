package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.feature.inference.appendonly.SidecarMarkerSpan
import com.browntowndev.enclave.feature.inference.appendonly.findNextSidecarMarkerSpan
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import java.security.MessageDigest

/**
 * Sidecar-focused prompt-cache diagnostics. Sidecar freeze/replay is the leading
 * suspect for DeepSeek prefix-cache misses; these logs correlate wire placement,
 * text identity, and JSON byte offsets turn-to-turn.
 */
internal object PromptCacheSidecarDiagnostics {
    /** DeepSeek block granularity is 128 tokens; ~4 chars/token as a rough byte window. */
    const val CACHE_BLOCK_CHAR_ESTIMATE = 512

    private const val CONTENT_PROBE_CHARS = 160
    private const val LOG_SLOT_PREVIEW_CHARS = 8

    data class SidecarSlotCapture(
        val mappedInsertIndex: Int,
        val textHash: String,
        val textLength: Int,
    )

    data class SidecarWireCapture(
        val frozenOnWire: List<SidecarSlotCapture>,
        val liveSidecarText: String?,
        val preRetrievalSidecarText: String?,
        val tailInsertIndex: Int,
        val mappedBodySize: Int,
        val frozenAfterFreezeCount: Int,
    )

    data class WireSystemPlacement(
        val wireIndex: Int,
        val kind: String,
        val textHash: String,
        val textLength: Int,
    )

    data class SidecarManifest(
        val frozenOnWire: List<SidecarSlotCapture>,
        val liveHash: String?,
        val liveLength: Int,
        val preRetrievalHash: String?,
        val tailInsertIndex: Int,
        val mappedBodySize: Int,
        val frozenAfterFreezeCount: Int,
        val wirePlacements: List<WireSystemPlacement>,
        val wireSystemCount: Int,
        val userContentSpans: List<UserContentSidecarSpan>,
    )

    data class UserContentSidecarSpan(
        val wireIndex: Int,
        val markerId: String,
        val bodyHash: String,
        val length: Int,
    )

    data class UserContentSpanDelta(
        val newSpans: List<UserContentSidecarSpan>,
        val unchangedSpans: List<UserContentSidecarSpan>,
        val removedSpans: List<UserContentSidecarSpan>,
    )

    data class SystemJsonSpan(
        val roleCharOffset: Int,
        val contentHash: String,
        val contentLength: Int,
    )

    data class SidecarDelta(
        val newFrozenSlots: List<SidecarSlotCapture>,
        val removedFrozenSlots: List<SidecarSlotCapture>,
        val unchangedFrozenSlots: List<SidecarSlotCapture>,
        val liveTextChanged: Boolean,
        val liveWasOnWirePreviously: Boolean,
        val liveIsOnWireNow: Boolean,
        val tailInsertIndexDelta: Int,
        val mappedBodySizeDelta: Int,
        val frozenAfterFreezeDelta: Int,
        val newWireSystemKinds: List<String>,
        val wireSystemIndexShifts: List<String>,
        val userContentSpanDelta: UserContentSpanDelta,
    )

    fun captureFrom(
        frozenOnWire: List<SidecarSlotCapture>,
        liveSidecarText: String?,
        preRetrievalSidecarText: String?,
        tailInsertIndex: Int,
        mappedBodySize: Int,
        frozenAfterFreezeCount: Int,
        stableSystemPrompt: String,
        messages: List<ChatCompletionMessageParam>,
    ): SidecarManifest {
        val stableHash = shortHash(stableSystemPrompt)
        val frozenHashes = frozenOnWire.map { it.textHash }.toSet()
        val liveHash = liveSidecarText?.let(::shortHash)
        val preRetrievalHash = preRetrievalSidecarText?.let(::shortHash)
        val wirePlacements =
            messages.mapIndexedNotNull { index, param ->
                if (!param.isSystem()) {
                    return@mapIndexedNotNull null
                }
                val text = param.asSystem().content().wireContentText()
                val hash = shortHash(text)
                val kind =
                    when {
                        hash == stableHash -> "stable_system"
                        hash in frozenHashes -> "frozen_sidecar"
                        liveHash != null && hash == liveHash -> "live_sidecar"
                        preRetrievalHash != null && hash == preRetrievalHash -> "pre_retrieval_sidecar"
                        else -> "unknown_system"
                    }
                WireSystemPlacement(
                    wireIndex = index,
                    kind = kind,
                    textHash = hash,
                    textLength = text.length,
                )
            }
        return SidecarManifest(
            frozenOnWire = frozenOnWire,
            liveHash = liveHash,
            liveLength = liveSidecarText?.length ?: 0,
            preRetrievalHash = preRetrievalHash,
            tailInsertIndex = tailInsertIndex,
            mappedBodySize = mappedBodySize,
            frozenAfterFreezeCount = frozenAfterFreezeCount,
            wirePlacements = wirePlacements,
            wireSystemCount = wirePlacements.size,
            userContentSpans = classifyUserContentSpans(messages),
        )
    }

    private fun classifyUserContentSpans(
        messages: List<ChatCompletionMessageParam>,
    ): List<UserContentSidecarSpan> {
        val spans = mutableListOf<UserContentSidecarSpan>()
        messages.forEachIndexed { wireIndex, param ->
            if (!param.isUser()) return@forEachIndexed
            val content = param.asUser().content()
            val text = when {
                content.isText() -> content.asText()
                content.isArrayOfContentParts() ->
                    content.asArrayOfContentParts()
                        .filter { it.isText() }
                        .joinToString("") { it.asText().text() }
                else -> return@forEachIndexed
            }
            var span: SidecarMarkerSpan? = findNextSidecarMarkerSpan(text, startIndex = 0)
            while (span != null) {
                val currentSpan = span
                val blockText = text.substring(currentSpan.blockStart, currentSpan.blockEndInclusive + 1)
                spans += UserContentSidecarSpan(
                    wireIndex = wireIndex,
                    markerId = currentSpan.markerId,
                    bodyHash = shortHash(blockText),
                    length = blockText.length,
                )
                span = findNextSidecarMarkerSpan(text, startIndex = currentSpan.blockEndInclusive + 1)
            }
        }
        return spans
    }

    fun diff(
        previous: SidecarManifest?,
        current: SidecarManifest,
    ): SidecarDelta? {
        if (previous == null) {
            return null
        }
        val prevFrozenByKey = previous.frozenOnWire.associateBy { slotKey(it) }
        val currFrozenByKey = current.frozenOnWire.associateBy { slotKey(it) }
        val newFrozen =
            current.frozenOnWire.filter { slotKey(it) !in prevFrozenByKey }
        val removedFrozen =
            previous.frozenOnWire.filter { slotKey(it) !in currFrozenByKey }
        val unchangedFrozen =
            current.frozenOnWire.filter { slot ->
                prevFrozenByKey[slotKey(slot)]?.textHash == slot.textHash
            }
        val prevKindsByHash =
            previous.wirePlacements.groupBy { it.textHash }.mapValues { (_, v) -> v.map { it.kind } }
        val newWireSystemKinds =
            current.wirePlacements
                .filter { placement ->
                    placement.kind != "stable_system" &&
                        prevKindsByHash[placement.textHash].isNullOrEmpty()
                }.map { "${it.wireIndex}:${it.kind}" }
        val wireSystemIndexShifts =
            previous.wirePlacements
                .filter { it.kind != "stable_system" }
                .mapNotNull { prevPlacement ->
                    val curr =
                        current.wirePlacements.firstOrNull {
                            it.textHash == prevPlacement.textHash && it.kind == prevPlacement.kind
                        }
                    if (curr == null || curr.wireIndex == prevPlacement.wireIndex) {
                        null
                    } else {
                        "${prevPlacement.kind}@${prevPlacement.wireIndex}->${curr.wireIndex}"
                    }
                }
        val prevSpansByKey = previous.userContentSpans.associateBy { it.markerId }
        val currSpansByKey = current.userContentSpans.associateBy { it.markerId }
        val newSpans = current.userContentSpans.filter { it.markerId !in prevSpansByKey }
        val removedSpans = previous.userContentSpans.filter { it.markerId !in currSpansByKey }
        val unchangedSpans = current.userContentSpans.filter { prevSpansByKey[it.markerId]?.bodyHash == it.bodyHash }
        return SidecarDelta(
            newFrozenSlots = newFrozen,
            removedFrozenSlots = removedFrozen,
            unchangedFrozenSlots = unchangedFrozen,
            liveTextChanged = previous.liveHash != current.liveHash,
            liveWasOnWirePreviously = previous.liveHash != null,
            liveIsOnWireNow = current.liveHash != null,
            tailInsertIndexDelta = current.tailInsertIndex - previous.tailInsertIndex,
            mappedBodySizeDelta = current.mappedBodySize - previous.mappedBodySize,
            frozenAfterFreezeDelta = current.frozenAfterFreezeCount - previous.frozenAfterFreezeCount,
            newWireSystemKinds = newWireSystemKinds,
            wireSystemIndexShifts = wireSystemIndexShifts,
            userContentSpanDelta = UserContentSpanDelta(
                newSpans = newSpans,
                unchangedSpans = unchangedSpans,
                removedSpans = removedSpans,
            ),
        )
    }

    fun extractSystemJsonSpans(wireJsonBody: String): List<SystemJsonSpan> {
        val spans = mutableListOf<SystemJsonSpan>()
        var searchFrom = 0
        while (searchFrom < wireJsonBody.length) {
            val roleIndex = wireJsonBody.indexOf("\"role\"", searchFrom)
            if (roleIndex < 0) {
                break
            }
            val parsed = parseSystemSpanAt(wireJsonBody, roleIndex)
            if (parsed != null) {
                spans += parsed
            }
            searchFrom = roleIndex + 6
        }
        return spans
    }

    private fun parseSystemSpanAt(
        wireJsonBody: String,
        roleIndex: Int,
    ): SystemJsonSpan? {
        val roleWindowEnd = (roleIndex + 32).coerceAtMost(wireJsonBody.length)
        val roleWindow = wireJsonBody.substring(roleIndex, roleWindowEnd)
        if (!roleWindow.contains("system")) {
            return null
        }
        val contentIndex = wireJsonBody.indexOf("\"content\"", roleIndex)
        if (contentIndex < 0 || contentIndex - roleIndex > 256) {
            return null
        }
        val contentStart = contentIndex + "\"content\"".length
        val probeEnd = (contentStart + CONTENT_PROBE_CHARS).coerceAtMost(wireJsonBody.length)
        val probe = wireJsonBody.substring(contentStart, probeEnd)
        return SystemJsonSpan(
            roleCharOffset = roleIndex,
            contentHash = shortHash(probe),
            contentLength = estimateJsonStringLength(wireJsonBody, contentStart),
        )
    }

    fun diffSystemJsonSpans(
        previous: List<SystemJsonSpan>,
        current: List<SystemJsonSpan>,
    ): String {
        if (previous.isEmpty() && current.isEmpty()) {
            return "none"
        }
        val parts = mutableListOf<String>()
        parts += "count=${previous.size}->${current.size}"
        val minShared = minOf(previous.size, current.size)
        for (index in 0 until minShared) {
            val prev = previous[index]
            val curr = current[index]
            if (prev.contentHash != curr.contentHash) {
                parts += "sys[$index]:hash@${prev.roleCharOffset}->${curr.roleCharOffset}"
            } else if (prev.roleCharOffset != curr.roleCharOffset) {
                parts += "sys[$index]:offset@${prev.roleCharOffset}->${curr.roleCharOffset}"
            }
        }
        if (current.size > previous.size) {
            current.drop(minShared).forEach { span ->
                parts += "insert@${span.roleCharOffset}:hash=${span.contentHash.take(LOG_SLOT_PREVIEW_CHARS)}"
            }
        }
        if (previous.size > current.size) {
            parts += "removed=${previous.size - current.size}"
        }
        return parts.joinToString(";")
    }

    fun formatManifest(manifest: SidecarManifest): String =
        buildString {
            append("frozenOnWire=${formatSlots(manifest.frozenOnWire)}")
            append(" live=")
            append(manifest.liveHash?.take(LOG_SLOT_PREVIEW_CHARS) ?: "none")
            append("(len=${manifest.liveLength})")
            append(" preRetrieval=")
            append(manifest.preRetrievalHash?.take(LOG_SLOT_PREVIEW_CHARS) ?: "none")
            append(" tailIdx=${manifest.tailInsertIndex}")
            append(" mappedSize=${manifest.mappedBodySize}")
            append(" frozenAfterFreeze=${manifest.frozenAfterFreezeCount}")
            append(" wireSys=")
            append(
                manifest.wirePlacements.joinToString(",") { placement ->
                    "${placement.wireIndex}:${placement.kind}:${placement.textHash.take(LOG_SLOT_PREVIEW_CHARS)}"
                },
            )
        }

    fun formatDelta(delta: SidecarDelta): String =
        buildString {
            append("newFrozen=${formatSlots(delta.newFrozenSlots)}")
            append(" removedFrozen=${formatSlots(delta.removedFrozenSlots)}")
            append(" unchangedFrozen=${delta.unchangedFrozenSlots.size}")
            append(" liveChanged=${delta.liveTextChanged}")
            append(" liveOnWire=${delta.liveWasOnWirePreviously}->${delta.liveIsOnWireNow}")
            append(" tailDelta=${delta.tailInsertIndexDelta}")
            append(" mappedDelta=${delta.mappedBodySizeDelta}")
            append(" frozenAfterDelta=${delta.frozenAfterFreezeDelta}")
            if (delta.newWireSystemKinds.isNotEmpty()) {
                append(" newWireSys=[${delta.newWireSystemKinds.joinToString(",")}]")
            }
            if (delta.wireSystemIndexShifts.isNotEmpty()) {
                append(" wireShifts=[${delta.wireSystemIndexShifts.joinToString(",")}]")
            }
            val newSz = delta.userContentSpanDelta.newSpans.size
            val remSz = delta.userContentSpanDelta.removedSpans.size
            val uncSz = delta.userContentSpanDelta.unchangedSpans.size
            append(" userContentSpansDelta=new:$newSz,removed:$remSz,unchanged:$uncSz")
        }

    fun formatBlock0(wireJsonBody: String): String {
        val block = wireJsonBody.take(CACHE_BLOCK_CHAR_ESTIMATE)
        return "block0Len=${block.length} block0Hash=${shortHash(block)}"
    }

    fun formatEarlyJsonBreak(
        previousJson: String,
        currentJson: String,
    ): String {
        val fullBreak =
            PromptCachePrefixDiagnostics.computePrefixBreak(
                previousBody = previousJson,
                newBody = currentJson,
                windowChars = PromptCachePrefixDiagnostics.PREFIX_BREAK_WINDOW_CHARS,
            )
        val blockBreak =
            PromptCachePrefixDiagnostics.computePrefixBreak(
                previousBody = previousJson.take(CACHE_BLOCK_CHAR_ESTIMATE),
                newBody = currentJson.take(CACHE_BLOCK_CHAR_ESTIMATE),
                windowChars = PromptCachePrefixDiagnostics.PREFIX_BREAK_WINDOW_CHARS,
            )
        return "fullMatch=${fullBreak.matchChars}/${fullBreak.newChars}(${fullBreak.matchPct}%) " +
            "block0Match=${blockBreak.matchChars}/${blockBreak.newChars}(${blockBreak.matchPct}%) " +
            "block0Div@${blockBreak.divergenceIndex} " +
            "block0Old=${PromptCachePrefixDiagnostics.sanitizeForLog(blockBreak.oldSnippet)} " +
            "block0New=${PromptCachePrefixDiagnostics.sanitizeForLog(blockBreak.newSnippet)}"
    }

    fun slotFrom(
        mappedInsertIndex: Int,
        text: String,
    ): SidecarSlotCapture =
        SidecarSlotCapture(
            mappedInsertIndex = mappedInsertIndex,
            textHash = shortHash(text),
            textLength = text.length,
        )

    private fun slotKey(slot: SidecarSlotCapture): String = "${slot.mappedInsertIndex}:${slot.textHash}"

    private fun formatSlots(slots: List<SidecarSlotCapture>): String =
        if (slots.isEmpty()) {
            "[]"
        } else {
            slots.joinToString("|") { slot ->
                "@${slot.mappedInsertIndex}:${slot.textHash.take(LOG_SLOT_PREVIEW_CHARS)}(len=${slot.textLength})"
            }
        }

    private fun estimateJsonStringLength(
        json: String,
        contentValueStart: Int,
    ): Int {
        var index = contentValueStart
        while (index < json.length && json[index].isWhitespace()) {
            index++
        }
        if (index >= json.length) {
            return 0
        }
        return when (json[index]) {
            '"' -> {
                var end = index + 1
                while (end < json.length) {
                    if (json[end] == '\\') {
                        end += 2
                        continue
                    }
                    if (json[end] == '"') {
                        return end - index - 1
                    }
                    end++
                }
                json.length - index
            }
            else -> CONTENT_PROBE_CHARS
        }
    }

    private fun ChatCompletionSystemMessageParam.Content.wireContentText(): String =
        when {
            isText() -> asText()
            isArrayOfContentParts() -> asArrayOfContentParts().joinToString("|") { it.text() }
            else -> "content_parts"
        }

    private fun shortHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }
}
