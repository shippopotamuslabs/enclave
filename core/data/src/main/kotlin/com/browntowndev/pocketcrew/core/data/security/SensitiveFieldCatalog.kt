package com.browntowndev.pocketcrew.core.data.security

interface SensitiveFieldCatalog {
    fun auditedOwners(): Set<String>

    fun sensitiveFields(owner: String): Set<String>

    fun isSensitive(
        owner: String,
        field: String,
    ): Boolean

    fun allRefs(): Set<SensitiveFieldRef>

    fun firestoreFields(collectionPath: String): Set<String>
}

class StaticSensitiveFieldCatalog : SensitiveFieldCatalog {
    override fun auditedOwners(): Set<String> = auditedOwners

    override fun sensitiveFields(owner: String): Set<String> = ownerFields[owner].orEmpty()

    override fun isSensitive(
        owner: String,
        field: String,
    ): Boolean = field in sensitiveFields(owner)

    override fun allRefs(): Set<SensitiveFieldRef> = refs

    override fun firestoreFields(collectionPath: String): Set<String> = firestoreFields[collectionPath].orEmpty()

    private companion object {
        private val roomRefs =
            listOf(
                "ChatEntity" to listOf("name"),
                "MessageEntity" to
                    listOf(
                        "content",
                        "imageUrisJson",
                        "thinkingRaw",
                        "artifactsJson",
                        "generatedMediaUrisJson",
                        "generatedMediaAssetIdsJson",
                        "generatedMediaPromptsJson",
                    ),
                "ChatSummaryEntity" to listOf("content"),
                "MemoriesEntity" to listOf("content"),
                "MessageVisionAnalysisEntity" to listOf("imageUri", "promptText", "analysisText"),
                "MessageFileEntity" to listOf("name", "content", "remoteUrl", "stagingRemoteUrl"),
                "WebSearchSourceEntity" to listOf("title", "url", "content"),
                "MessageTranscriptSegmentEntity" to listOf("text"),
                "ApiModelConfigurationEntity" to listOf("displayName", "customHeaders", "systemPrompt"),
                "LocalModelConfigurationEntity" to listOf("displayName", "systemPrompt"),
                "TtsProviderEntity" to listOf("displayName", "voiceName", "modelName", "baseUrl"),
                "MediaProviderEntity" to listOf("displayName", "modelName", "baseUrl"),
                "ApiCredentialsEntity" to listOf("displayName", "baseUrl", "encryptedApiKey"),
                "StudioAlbumEntity" to listOf("name"),
                "StudioMediaEntity" to listOf("prompt", "mediaUri", "remoteUrl", "remoteThumbnailUrl", "stagingRemoteUrl"),
                "DocumentEntity" to listOf("json", "title", "author"),
            )

        private val nonRoomRefs =
            listOf(
                SensitiveFieldRef("SettingsRepositoryImpl", "settings_custom_prompt_text", PersistenceBoundary.DATASTORE),
                SensitiveFieldRef("PipelineStateRepositoryImpl", "pipeline_state_chatId", PersistenceBoundary.DATASTORE),
                SensitiveFieldRef("ApiKeyManager", "api_key_value", PersistenceBoundary.ENCRYPTED_SHARED_PREFERENCES, "api_keys"),
            )

        private val refs =
            roomRefs
                .flatMap { (owner, fields) ->
                    fields.map { field -> SensitiveFieldRef(owner, field, PersistenceBoundary.ROOM) }
                }.toSet() + nonRoomRefs

        private val ownerFields = refs.groupBy({ it.owner }, { it.field }).mapValues { it.value.toSet() }

        private val auditedOwners =
            ownerFields.keys +
                setOf(
                    "DefaultModelEntity",
                    "LocalModelEntity",
                    "EmbeddingEntity",
                    "ApiKeyManager.pending_alias_sets",
                    "LocalVaultMetadataStore",
                    "AndroidBiometricManager",
                    "DownloadSessionManager",
                    "Firestore.vault_metadata",
                )

        private val firestoreFields =
            mapOf(
                "chats" to setOf("name"),
                "messages" to
                    setOf(
                        "content",
                        "image_uris_json",
                        "thinking_raw",
                        "artifacts_json",
                        "generated_media_uris_json",
                        "generated_media_asset_ids_json",
                        "generated_media_prompts_json",
                    ),
                "chat_summaries" to setOf("content"),
                "memories" to setOf("content"),
                "vision_analysis" to setOf("image_uri", "prompt_text", "analysis_text"),
                "message_files" to setOf("name", "content", "remote_url", "staging_remote_url"),
                "web_search_sources" to setOf("title", "url", "content"),
                "message_transcript_segments" to setOf("text"),
                "api_model_configs" to setOf("display_name", "custom_headers", "system_prompt"),
                "local_model_configs" to setOf("display_name", "system_prompt"),
                "tts_providers" to setOf("display_name", "voice_name", "model_name", "base_url"),
                "media_providers" to setOf("display_name", "model_name", "base_url"),
                "api_credentials" to setOf("display_name", "base_url"),
                "studio_albums" to setOf("name"),
                "studio_gallery" to setOf("prompt", "media_uri", "remote_url", "remote_thumbnail_url", "staging_remote_url"),
                "documents" to setOf("json", "title", "author"),
            )
    }
}
