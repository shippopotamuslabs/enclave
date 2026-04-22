package com.browntowndev.pocketcrew.feature.chat.service

import android.app.Service

internal object ChatInferenceNotificationPolicy {
    const val FOREGROUND_STOP_MODE = Service.STOP_FOREGROUND_REMOVE

    fun shouldShowCompletionNotification(isAppForeground: Boolean): Boolean {
        return !isAppForeground
    }
}
