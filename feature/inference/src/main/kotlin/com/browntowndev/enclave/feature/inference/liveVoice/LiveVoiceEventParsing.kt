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

fun parseOpenAiRealtimeEvent(json: String): LiveVoiceEvent? {
    val root =
        runCatching { liveVoiceJson.parseToJsonElement(json).jsonObject }
            .getOrNull() ?: return null
    val type = root["type"]?.jsonPrimitive?.contentOrNull ?: return null

    return when (type) {
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

        "response.done",
        "response.completed",
        "response.audio.done",
        "response.output_audio.done",
        -> LiveVoiceEvent.AssistantResponseComplete

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
}

fun parseGeminiLiveEvent(json: String): LiveVoiceEvent? {
    val root =
        runCatching { liveVoiceJson.parseToJsonElement(json).jsonObject }
            .getOrNull() ?: return null

    return when {
        root.containsKey("setupComplete") -> LiveVoiceEvent.Connected
        else -> parseGeminiServerContent(root["serverContent"]?.jsonObject)
    }
}

private fun parseGeminiServerContent(serverContent: JsonObject?): LiveVoiceEvent? {
    if (serverContent == null) return null

    return when {
        serverContent["turnComplete"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true ->
            LiveVoiceEvent.AssistantResponseComplete

        else ->
            parseGeminiServerContentPayload(serverContent)
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
