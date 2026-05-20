package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.core.data.security.PersistenceBoundary
import com.browntowndev.pocketcrew.core.data.security.SensitiveFieldRef

/**
 * Maps Firestore collection paths and snake_case field names to [SensitiveFieldRef] rows used
 * for AAD construction (Room owner + Kotlin property field + Firestore document id).
 */
internal object FirestoreSensitiveFieldRefMapping {
    private val firestoreCollectionToRoomOwner =
        mapOf(
            "chats" to "ChatEntity",
            "messages" to "MessageEntity",
            "chat_summaries" to "ChatSummaryEntity",
            "memories" to "MemoriesEntity",
            "vision_analysis" to "MessageVisionAnalysisEntity",
            "message_files" to "MessageFileEntity",
            "web_search_sources" to "WebSearchSourceEntity",
            "message_transcript_segments" to "MessageTranscriptSegmentEntity",
            "api_model_configs" to "ApiModelConfigurationEntity",
            "local_model_configs" to "LocalModelConfigurationEntity",
            "tts_providers" to "TtsProviderEntity",
            "media_providers" to "MediaProviderEntity",
            "api_credentials" to "ApiCredentialsEntity",
            "studio_albums" to "StudioAlbumEntity",
            "studio_gallery" to "StudioMediaEntity",
            "documents" to "DocumentEntity",
        )

    private val firestoreSnakeToRoomFieldOverrides =
        mapOf(
            "messages" to
                mapOf(
                    "image_uris_json" to "imageUrisJson",
                    "thinking_raw" to "thinkingRaw",
                    "artifacts_json" to "artifactsJson",
                    "generated_media_uris_json" to "generatedMediaUrisJson",
                    "generated_media_asset_ids_json" to "generatedMediaAssetIdsJson",
                    "generated_media_prompts_json" to "generatedMediaPromptsJson",
                ),
            "vision_analysis" to
                mapOf(
                    "image_uri" to "imageUri",
                    "prompt_text" to "promptText",
                    "analysis_text" to "analysisText",
                ),
            "message_files" to
                mapOf(
                    "remote_url" to "remoteUrl",
                    "staging_remote_url" to "stagingRemoteUrl",
                ),
            "api_model_configs" to
                mapOf(
                    "display_name" to "displayName",
                    "custom_headers" to "customHeaders",
                    "system_prompt" to "systemPrompt",
                ),
            "local_model_configs" to
                mapOf(
                    "display_name" to "displayName",
                    "system_prompt" to "systemPrompt",
                ),
            "tts_providers" to
                mapOf(
                    "display_name" to "displayName",
                    "voice_name" to "voiceName",
                    "model_name" to "modelName",
                    "base_url" to "baseUrl",
                ),
            "media_providers" to
                mapOf(
                    "display_name" to "displayName",
                    "model_name" to "modelName",
                    "base_url" to "baseUrl",
                ),
            "api_credentials" to
                mapOf(
                    "display_name" to "displayName",
                    "base_url" to "baseUrl",
                ),
            "studio_gallery" to
                mapOf(
                    "remote_url" to "remoteUrl",
                    "remote_thumbnail_url" to "remoteThumbnailUrl",
                    "staging_remote_url" to "stagingRemoteUrl",
                    "media_uri" to "mediaUri",
                ),
        )

    fun sensitiveFieldRef(
        collectionPath: String,
        firestoreSnakeField: String,
    ): SensitiveFieldRef {
        val roomOwner = firestoreCollectionToRoomOwner[collectionPath] ?: collectionPath
        val roomField = firestoreFieldToRoomField(collectionPath, firestoreSnakeField)
        return SensitiveFieldRef(
            owner = roomOwner,
            field = roomField,
            boundary = PersistenceBoundary.FIRESTORE,
            collectionPath = collectionPath,
        )
    }

    private fun firestoreFieldToRoomField(
        collectionPath: String,
        firestoreField: String,
    ): String =
        firestoreSnakeToRoomFieldOverrides[collectionPath]?.get(firestoreField)
            ?: snakeToLowerCamel(firestoreField)

    private fun snakeToLowerCamel(snake: String): String {
        if (!snake.contains('_')) return snake
        return snake
            .split('_')
            .mapIndexed { i, part ->
                if (i == 0) part else part.replaceFirstChar { c -> c.uppercase() }
            }.joinToString("")
    }
}
