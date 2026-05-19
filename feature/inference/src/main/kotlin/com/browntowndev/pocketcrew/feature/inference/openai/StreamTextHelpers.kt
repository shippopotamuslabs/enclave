package com.browntowndev.pocketcrew.feature.inference.openai

internal fun appendStreamDelta(
    parts: MutableMap<String, StringBuilder>,
    key: String,
    text: String,
) {
    if (text.isEmpty()) return
    parts.getOrPut(key) { StringBuilder() }.append(text)
}

internal fun resolveNovelStreamText(
    parts: MutableMap<String, StringBuilder>,
    key: String,
    finalizedText: String,
): String {
    if (finalizedText.isEmpty()) return ""
    val priorText = parts[key]?.toString().orEmpty()
    val novelText = novelStreamSuffix(priorText, finalizedText)
    parts[key] = StringBuilder(finalizedText)
    return novelText
}

internal fun novelStreamSuffix(
    streamedText: String,
    finalizedText: String,
): String {
    if (finalizedText.isEmpty()) return ""
    if (streamedText.isEmpty()) return finalizedText
    if (streamedText == finalizedText) return ""
    val commonPrefixLength = streamedText.commonPrefixWith(finalizedText).length
    return finalizedText.drop(commonPrefixLength)
}

internal fun streamPartKey(
    itemId: String,
    outputIndex: Long,
    contentIndex: Long,
): String = "$itemId:$outputIndex:$contentIndex"

internal fun streamItemKey(
    itemId: String,
    outputIndex: Long,
): String = "$itemId:$outputIndex"

internal fun truncateForLog(
    text: String,
    maxLen: Int = 400,
): String = if (text.length <= maxLen) text else text.take(maxLen) + "…"
