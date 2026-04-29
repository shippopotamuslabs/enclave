package com.browntowndev.pocketcrew.presentation.navigation

import com.browntowndev.pocketcrew.feature.chat.service.ChatNotificationDeepLink

object Routes {
    const val CHAT = "chat"
    const val CHAT_WITH_ID = "chat?chatId={chatId}"
    const val CHAT_DEEP_LINK_PATTERN = ChatNotificationDeepLink.URI_PATTERN
    const val HISTORY = "history"
    const val STUDIO = "studio"
    const val STUDIO_DETAIL = "studio_detail?assetId={assetId}"
    const val GALLERY = "gallery"
    const val SETTINGS_GRAPH = "settings_graph"
    const val SETTINGS_MAIN = "settings_main"
    const val MODEL_DOWNLOAD = "model_download"
    const val BYOK_CONFIGURE = "byok_configure"
}
