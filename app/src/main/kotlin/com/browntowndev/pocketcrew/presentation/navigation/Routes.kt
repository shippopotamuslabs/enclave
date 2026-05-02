package com.browntowndev.pocketcrew.presentation.navigation

import com.browntowndev.pocketcrew.feature.chat.service.ChatNotificationDeepLink

object Routes {
    const val CHAT = "chat"
    const val CHAT_WITH_ID = "chat?chatId={chatId}"
    const val CHAT_DEEP_LINK_PATTERN = ChatNotificationDeepLink.URI_PATTERN
    const val HISTORY = "history"
    const val STUDIO = "studio"
    const val STUDIO_WITH_ARGS = "studio?editAssetId={editAssetId}&animateAssetId={animateAssetId}&animatePrompt={animatePrompt}&autoAnimate={autoAnimate}"
    const val STUDIO_DETAIL = "studio_detail?assetId={assetId}&animatePrompt={animatePrompt}&autoAnimate={autoAnimate}"
    const val GALLERY = "gallery"
    const val GALLERY_DETAIL = "gallery_detail?albumId={albumId}&assetId={assetId}"
    const val SETTINGS_GRAPH = "settings_graph"
    const val SETTINGS_MAIN = "settings_main"
    const val MODEL_DOWNLOAD = "model_download"
    const val BYOK_CONFIGURE = "byok_configure"
    const val ARTIFACT = "artifact"
    const val ARTIFACT_WITH_ARGS = "artifact?content={content}&title={title}"

    fun studioWithEditAsset(assetId: String): String = "$STUDIO?editAssetId=$assetId"

    fun studioWithAnimateAsset(assetId: String, autoAnimate: Boolean = false, prompt: String? = null): String {
        return "$STUDIO?animateAssetId=$assetId&autoAnimate=$autoAnimate" + 
            if (prompt != null) "&animatePrompt=$prompt" else ""
    }

    fun studioDetailWithAnimateAsset(assetId: String, autoAnimate: Boolean = false, prompt: String? = null): String {
        return "studio_detail?assetId=$assetId&autoAnimate=$autoAnimate" +
            if (prompt != null) "&animatePrompt=$prompt" else ""
    }

    fun artifact(title: String, content: String): String {
        return "$ARTIFACT?title=$title&content=$content"
    }
}
