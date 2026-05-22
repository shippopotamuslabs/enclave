package com.browntowndev.enclave.core.data.remote.firestore

/**
 * Declarative collection → required-parent map. Mirrors the Room foreign-key topology
 * and the adapter payload field names. Used by [FirestoreSyncEventApplier] to fetch
 * and apply parents before a child row so we never need placeholder rows.
 */
internal object FirestoreSyncDependencyGraph {
    data class ParentRef(
        val collectionPath: String,
        val entityId: String,
    )

    private val NO_PARENTS: (Map<String, Any?>) -> List<ParentRef> = { emptyList() }

    private val MESSAGE_PARENTS: (Map<String, Any?>) -> List<ParentRef> = { source ->
        val chatId = source["chat_id"] as? String
        val userMessageId = source["user_message_id"] as? String
        buildList {
            if (!chatId.isNullOrBlank()) add(ParentRef("chats", chatId))
            if (!userMessageId.isNullOrBlank()) add(ParentRef("messages", userMessageId))
        }
    }

    private val CHAT_SUMMARY_PARENTS: (Map<String, Any?>) -> List<ParentRef> = { source ->
        val chatId = source["chat_id"] as? String
        val lastMessageId = source["last_summarized_message_id"] as? String
        buildList {
            if (!chatId.isNullOrBlank()) add(ParentRef("chats", chatId))
            if (!lastMessageId.isNullOrBlank()) add(ParentRef("messages", lastMessageId))
        }
    }

    private val MESSAGE_CHILD_PARENTS: (Map<String, Any?>) -> List<ParentRef> = { source ->
        val messageId = source["message_id"] as? String
        val chatId = source["chat_id"] as? String
        buildList {
            if (!chatId.isNullOrBlank()) add(ParentRef("chats", chatId))
            if (!messageId.isNullOrBlank()) add(ParentRef("messages", messageId))
        }
    }

    private val VISION_PARENTS: (Map<String, Any?>) -> List<ParentRef> = { source ->
        val userMessageId = source["user_message_id"] as? String
        val chatId = source["chat_id"] as? String
        buildList {
            if (!chatId.isNullOrBlank()) add(ParentRef("chats", chatId))
            if (!userMessageId.isNullOrBlank()) add(ParentRef("messages", userMessageId))
        }
    }

    private val LOCAL_MODEL_CONFIG_PARENTS: (Map<String, Any?>) -> List<ParentRef> = { source ->
        val localModelId = source["local_model_id"] as? String
        buildList { if (!localModelId.isNullOrBlank()) add(ParentRef("local_models", localModelId)) }
    }

    private val API_MODEL_CONFIG_PARENTS: (Map<String, Any?>) -> List<ParentRef> = { source ->
        val credentialsId = source["api_credentials_id"] as? String
        buildList { if (!credentialsId.isNullOrBlank()) add(ParentRef("api_credentials", credentialsId)) }
    }

    private val DEFAULT_MODEL_PARENTS: (Map<String, Any?>) -> List<ParentRef> = { source ->
        val localModelConfigId = source["local_model_config_id"] as? String
        val apiModelConfigId = source["api_model_config_id"] as? String
        val ttsProviderId = source["tts_provider_id"] as? String
        val mediaProviderId = source["media_provider_id"] as? String
        buildList {
            if (!localModelConfigId.isNullOrBlank()) {
                add(ParentRef("local_model_configs", localModelConfigId))
            }
            if (!apiModelConfigId.isNullOrBlank()) {
                add(ParentRef("api_model_configs", apiModelConfigId))
            }
            if (!ttsProviderId.isNullOrBlank()) add(ParentRef("tts_providers", ttsProviderId))
            if (!mediaProviderId.isNullOrBlank()) {
                add(ParentRef("media_providers", mediaProviderId))
            }
        }
    }

    private val STUDIO_GALLERY_PARENTS: (Map<String, Any?>) -> List<ParentRef> = { source ->
        val albumId = source["album_id"] as? String
        buildList { if (!albumId.isNullOrBlank()) add(ParentRef("studio_albums", albumId)) }
    }

    private val GRAPH: Map<String, (Map<String, Any?>) -> List<ParentRef>> = mapOf(
        "chats" to NO_PARENTS,
        "messages" to MESSAGE_PARENTS,
        "chat_summaries" to CHAT_SUMMARY_PARENTS,
        "memories" to NO_PARENTS,
        "vision_analysis" to VISION_PARENTS,
        "message_files" to MESSAGE_CHILD_PARENTS,
        "web_search_sources" to MESSAGE_CHILD_PARENTS,
        "message_transcript_segments" to MESSAGE_CHILD_PARENTS,
        "documents" to MESSAGE_CHILD_PARENTS,
        "api_credentials" to NO_PARENTS,
        "api_keys" to NO_PARENTS,
        "api_model_configs" to API_MODEL_CONFIG_PARENTS,
        "local_models" to NO_PARENTS,
        "local_model_configs" to LOCAL_MODEL_CONFIG_PARENTS,
        "default_models" to DEFAULT_MODEL_PARENTS,
        "tts_providers" to NO_PARENTS,
        "media_providers" to NO_PARENTS,
        "studio_albums" to NO_PARENTS,
        "studio_gallery" to STUDIO_GALLERY_PARENTS,
    )

    fun collectionPaths(): Set<String> = GRAPH.keys

    fun requiredParents(
        collectionPath: String,
        sourceData: Map<String, Any?>,
    ): List<ParentRef> = GRAPH[collectionPath]?.invoke(sourceData) ?: emptyList()
}
