package com.browntowndev.enclave.core.data.remote.firestore

/**
 * Dependency-ordered collection scan shared by entity reconciliation and the Functions
 * backfill callable ([BACKFILL_COLLECTION_ORDER] in `syncEventFeed.ts`).
 */
internal object FirestoreSyncCollectionOrder {
    val reconciliationOrder: List<String> =
        listOf(
            "chats",
            "memories",
            "api_credentials",
            "local_models",
            "utility_models",
            "tts_providers",
            "live_voice_providers",
            "media_providers",
            "studio_albums",
            "messages",
            "chat_summaries",
            "message_files",
            "web_search_sources",
            "message_transcript_segments",
            "message_inference_telemetry",
            "vision_analysis",
            "documents",
            "local_model_configs",
            "api_model_configs",
            "default_models",
            "api_keys",
            "studio_gallery",
        )
}
