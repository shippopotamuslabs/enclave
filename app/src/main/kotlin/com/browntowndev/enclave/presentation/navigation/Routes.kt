package com.browntowndev.enclave.presentation.navigation

import com.browntowndev.enclave.feature.chat.service.ChatNotificationDeepLink

object Routes {
    const val CHAT = "chat"
    const val CHAT_WITH_ID = "chat?chatId={chatId}"
    const val CHAT_DEEP_LINK_PATTERN = ChatNotificationDeepLink.URI_PATTERN
    const val HISTORY = "history"
    const val STUDIO = "studio"
    const val STUDIO_DEEP_LINK_PATTERN = "enclave://studio"

    @Suppress("MaxLineLength")
    const val STUDIO_WITH_ARGS =
        "studio?editAssetId={editAssetId}&animateAssetId={animateAssetId}" +
            "&animatePrompt={animatePrompt}&autoAnimate={autoAnimate}" +
            "&rehydrateIds={rehydrateIds}&rehydrateUris={rehydrateUris}" +
            "&rehydrateTypes={rehydrateTypes}&rehydratePrompts={rehydratePrompts}"
    const val STUDIO_DETAIL = "studio_detail?assetId={assetId}&animatePrompt={animatePrompt}&autoAnimate={autoAnimate}"
    const val GALLERY = "gallery"
    const val GALLERY_DETAIL = "gallery_detail?albumId={albumId}&assetId={assetId}"
    const val SETTINGS_GRAPH = "settings_graph"
    const val SETTINGS_MAIN = "settings_main"
    const val MODEL_DOWNLOAD = "model_download"
    const val ONBOARDING = "onboarding"
    const val BYOK_CONFIGURE = "byok_configure"
    const val PROFILE_BUILDER_GRAPH = "profile_builder_graph"
    const val PROFILE_BUILDER_MAIN = "profile_builder_main"

    const val SHARED_CHAT = "shared?shareId={shareId}&key={key}"
    const val SHARED_CHAT_DEEP_LINK_PATTERN = "enclave://shared/{shareId}?key={key}"

    fun studioWithEditAsset(assetId: String): String = "$STUDIO?editAssetId=$assetId"

    fun studioWithAnimateAsset(
        assetId: String,
        autoAnimate: Boolean = false,
        prompt: String? = null,
    ): String =
        "$STUDIO?animateAssetId=$assetId&autoAnimate=$autoAnimate" +
            if (prompt != null) "&animatePrompt=$prompt" else ""

    fun studioDetailWithAnimateAsset(
        assetId: String,
        autoAnimate: Boolean = false,
        prompt: String? = null,
    ): String =
        "studio_detail?assetId=$assetId&autoAnimate=$autoAnimate" +
            if (prompt != null) "&animatePrompt=$prompt" else ""

    fun studioWithRehydration(
        ids: List<String>,
        uris: List<String>,
        types: List<String>,
        prompts: List<String>,
    ): String {
        val json = kotlinx.serialization.json.Json
        val encodedIds = android.net.Uri.encode(json.encodeToString(ids))
        val encodedUris = android.net.Uri.encode(json.encodeToString(uris))
        val encodedTypes = android.net.Uri.encode(json.encodeToString(types))
        val encodedPrompts = android.net.Uri.encode(json.encodeToString(prompts))

        return "$STUDIO?rehydrateIds=$encodedIds&rehydrateUris=$encodedUris&rehydrateTypes=$encodedTypes&rehydratePrompts=$encodedPrompts"
    }
}
