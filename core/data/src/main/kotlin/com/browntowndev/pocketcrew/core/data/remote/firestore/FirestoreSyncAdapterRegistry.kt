package com.browntowndev.pocketcrew.core.data.remote.firestore

import com.browntowndev.pocketcrew.core.data.remote.EntitySyncAdapter
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.ApiCredentialsSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.ApiKeySyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.ApiModelConfigSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.ChatSummarySyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.ChatSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.DefaultModelSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.DocumentSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.LocalModelConfigSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.LocalModelSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.MediaProviderSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.MemoriesSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.MessageFileSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.MessageSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.MessageTranscriptSegmentSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.StudioAlbumSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.StudioMediaSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.TtsProviderSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.VisionAnalysisSyncHandler
import com.browntowndev.pocketcrew.core.data.remote.firestore.handlers.WebSearchSourceSyncHandler

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
            ApiModelConfigSyncHandler(ctx),
            LocalModelConfigSyncHandler(ctx),
            DefaultModelSyncHandler(ctx),
            LocalModelSyncHandler(ctx),
            TtsProviderSyncHandler(ctx),
            MediaProviderSyncHandler(ctx),
            ApiCredentialsSyncHandler(ctx),
            ApiKeySyncHandler(ctx),
            StudioAlbumSyncHandler(ctx),
            StudioMediaSyncHandler(ctx),
            DocumentSyncHandler(ctx),
        )
}
