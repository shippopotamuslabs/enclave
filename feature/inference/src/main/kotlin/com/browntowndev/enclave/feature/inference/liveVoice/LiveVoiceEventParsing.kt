package com.browntowndev.enclave.feature.inference.liveVoice

import com.browntowndev.enclave.domain.port.media.LiveVoiceEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

private val liveVoiceJson =
    Json {
        ignoreUnknownKeys = true
    }

fun parseOpenAiRealtimeEvents(json: String): List<LiveVoiceEvent> {
    val root =
        runCatching { liveVoiceJson.parseToJsonElement(json).jsonObject }
            .getOrNull() ?: return emptyList()
    val type = root["type"]?.jsonPrimitive?.contentOrNull ?: return emptyList()

    return when (type) {
        "response.output_item.done" -> parseOpenAiOutputItemDone(root)
        "response.done", "response.completed" -> parseOpenAiResponseDone(root)
        else -> parseOpenAiRealtimeSingleEvent(root, type)?.let { listOf(it) } ?: emptyList()
    }
}

fun parseOpenAiRealtimeEvent(json: String): LiveVoiceEvent? =
    parseOpenAiRealtimeEvents(json).singleOrNull()

fun parseGeminiLiveEvents(json: String): List<LiveVoiceEvent> {
    val root =
        runCatching { liveVoiceJson.parseToJsonElement(json).jsonObject }
            .getOrNull() ?: return emptyList()

    if (root.containsKey("setupComplete")) return listOf(LiveVoiceEvent.Connected)

    val serverContent = root["serverContent"]?.jsonObject ?: return emptyList()
    val toolCallEvents = parseGeminiToolCallEvents(serverContent)
    if (toolCallEvents.isNotEmpty()) return toolCallEvents

    return parseGeminiLiveSingleEvent(root)?.let { listOf(it) } ?: emptyList()
}

fun parseGeminiLiveEvent(json: String): LiveVoiceEvent? =
    parseGeminiLiveEvents(json).singleOrNull()

private fun parseOpenAiOutputItemDone(root: JsonObject): List<LiveVoiceEvent> {
    val item = root["item"]?.jsonObject
    val toolCall =
        if (item != null && item["type"]?.jsonPrimitive?.contentOrNull == "function_call") {
            val callId = item["call_id"]?.jsonPrimitive?.contentOrNull
            val name = item["name"]?.jsonPrimitive?.contentOrNull
            val arguments = item["arguments"]?.jsonPrimitive?.contentOrNull
            if (callId != null && name != null && arguments != null) {
                LiveVoiceEvent.ToolCallRequested(callId, name, arguments)
            } else {
                null
            }
        } else {
            null
        }
    return toolCall?.let { listOf(it) } ?: emptyList()
}

private fun parseOpenAiResponseDone(root: JsonObject): List<LiveVoiceEvent> {
    val events = mutableListOf<LiveVoiceEvent>()
    root["response"]
        ?.jsonObject
        ?.get("output")
        ?.jsonArray
        ?.forEach { item ->
            val itemObject = item.jsonObject
            if (itemObject["type"]?.jsonPrimitive?.contentOrNull != "function_call") return@forEach
            val callId = itemObject["call_id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val name = itemObject["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val arguments = itemObject["arguments"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            events.add(LiveVoiceEvent.ToolCallRequested(callId, name, arguments))
        }
    events.add(LiveVoiceEvent.AssistantResponseComplete)
    return events
}

private fun parseOpenAiRealtimeSingleEvent(
    root: JsonObject,
    type: String,
): LiveVoiceEvent? =
    when (type) {
        "conversation.item.input_audio_transcription.delta" ->
            root["delta"]?.jsonPrimitive?.contentOrNull?.let(LiveVoiceEvent::UserTranscriptDelta)

        "conversation.item.input_audio_transcription.completed",
        "conversation.item.input_audio_transcription.updated",
        ->
            root["transcript"]?.jsonPrimitive?.contentOrNull?.let(LiveVoiceEvent::UserTranscriptDone)

        "response.audio_transcript.delta",
        "response.output_audio_transcript.delta",
        ->
            root["delta"]?.jsonPrimitive?.contentOrNull?.let(LiveVoiceEvent::AssistantTranscriptDelta)

        "response.audio_transcript.done",
        "response.output_audio_transcript.done",
        ->
            root["transcript"]?.jsonPrimitive?.contentOrNull?.let(LiveVoiceEvent::AssistantTranscriptDone)

        "response.audio.delta",
        "response.output_audio.delta",
        ->
            root["delta"]?.jsonPrimitive?.contentOrNull?.decodeBase64Pcm16()?.let(LiveVoiceEvent::AssistantAudioChunk)

        "input_audio_buffer.speech_started" -> LiveVoiceEvent.SpeechStarted
        "input_audio_buffer.speech_stopped" -> LiveVoiceEvent.SpeechStopped
        "session.created" -> LiveVoiceEvent.Connected

        "error" -> {
            val message =
                root["error"]
                    ?.jsonObject
                    ?.get("message")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: "Unknown error"
            LiveVoiceEvent.Error(message)
        }

        else -> null
    }

private fun parseGeminiToolCallEvents(serverContent: JsonObject): List<LiveVoiceEvent> {
    val functionCalls =
        serverContent["toolCall"]
            ?.jsonObject
            ?.get("functionCalls")
            ?.jsonArray
            ?: return emptyList()
    return functionCalls.mapNotNull { call ->
        val callObject = call.jsonObject
        val id = callObject["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val name = callObject["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val args = callObject["args"]?.jsonObject ?: return@mapNotNull null
        LiveVoiceEvent.ToolCallRequested(id, name, args.toString())
    }
}

private fun parseGeminiLiveSingleEvent(root: JsonObject): LiveVoiceEvent? {
    val serverContent = root["serverContent"]?.jsonObject ?: return null
    return when {
        serverContent["turnComplete"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true ->
            LiveVoiceEvent.AssistantResponseComplete

        else -> parseGeminiServerContentPayload(serverContent)
    }
}

private fun parseGeminiServerContentPayload(serverContent: JsonObject): LiveVoiceEvent? {
    val userTranscript =
        serverContent["inputTranscription"]
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.contentOrNull
    if (userTranscript != null) {
        return LiveVoiceEvent.UserTranscriptDone(userTranscript)
    }

    val assistantDelta =
        serverContent["outputTranscription"]
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.contentOrNull
    if (assistantDelta != null) {
        return LiveVoiceEvent.AssistantTranscriptDelta(assistantDelta)
    }

    val parts = serverContent["modelTurn"]?.jsonObject?.get("parts")?.jsonArray ?: return null
    return parts.firstNotNullOfOrNull { part -> parseGeminiAudioPart(part.jsonObject) }
}

private fun parseGeminiAudioPart(part: JsonObject): LiveVoiceEvent? {
    val inlineData = part["inlineData"]?.jsonObject ?: return null
    val mimeType = inlineData["mimeType"]?.jsonPrimitive?.contentOrNull
    if (mimeType != "audio/pcm") return null
    val data = inlineData["data"]?.jsonPrimitive?.contentOrNull ?: return null
    val pcm16 = data.decodeBase64Pcm16() ?: return null
    return LiveVoiceEvent.AssistantAudioChunk(pcm16)
}

private fun String.decodeBase64Pcm16(): ByteArray? =
    runCatching { Base64.getDecoder().decode(this) }.getOrNull()
