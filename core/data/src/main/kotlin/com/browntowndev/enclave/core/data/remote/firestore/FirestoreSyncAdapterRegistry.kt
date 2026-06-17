package com.browntowndev.enclave.core.data.remote.firestore

import com.browntowndev.enclave.core.data.remote.EntitySyncAdapter
import com.browntowndev.enclave.core.data.remote.firestore.handlers.ApiCredentialsSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.ApiKeySyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.ApiModelConfigSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.ChatSummarySyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.ChatSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.DefaultModelSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.DocumentSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.LiveVoiceProviderSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.LocalModelConfigSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.LocalModelSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.MediaProviderSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.MemoriesSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.MessageFileSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.MessageInferenceTelemetrySyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.MessageSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.MessageTranscriptSegmentSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.SearchToolSettingsSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.StudioAlbumSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.StudioMediaSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.TtsProviderSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.UtilityModelSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.VisionAnalysisSyncHandler
import com.browntowndev.enclave.core.data.remote.firestore.handlers.WebSearchSourceSyncHandler

internal class FirestoreSyncAdapterRegistry(
    ctx: FirestoreSyncContext,
) {
    val adapters: List<EntitySyncAdapter<*>> =
        listOf<EntitySyncAdapter<*>>(
            ChatSyncHandler(ctx),
            MessageSyncHandler(ctx),
            ChatSummarySyncHandler(ctx),
            MemoriesSyncHandler(ctx),
            VisionAnalysisSyncHandler(ctx),
            MessageFileSyncHandler(ctx),
            WebSearchSourceSyncHandler(ctx),
            MessageTranscriptSegmentSyncHandler(ctx),
            MessageInferenceTelemetrySyncHandler(ctx),
            // Config uploads: parents before children so Firestore always has FK targets
            // before default_models (and api/local configs) reference them.
            ApiCredentialsSyncHandler(ctx),
            LocalModelSyncHandler(ctx),
            TtsProviderSyncHandler(ctx),
            LiveVoiceProviderSyncHandler(ctx),
            MediaProviderSyncHandler(ctx),
            UtilityModelSyncHandler(ctx),
            ApiModelConfigSyncHandler(ctx),
            LocalModelConfigSyncHandler(ctx),
            DefaultModelSyncHandler(ctx),
            ApiKeySyncHandler(ctx),
            StudioAlbumSyncHandler(ctx),
            StudioMediaSyncHandler(ctx),
            DocumentSyncHandler(ctx),
            SearchToolSettingsSyncHandler(ctx),
        )
}
