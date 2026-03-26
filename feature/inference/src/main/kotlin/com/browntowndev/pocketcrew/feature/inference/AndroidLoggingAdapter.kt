package com.browntowndev.pocketcrew.feature.inference

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import javax.inject.Inject

/**
 * Implementation of [LoggingPort] that uses Android's Log utility.
 * This adapter lives in the inference layer where Android SDK is available.
 */
class AndroidLoggingAdapter @Inject constructor() : LoggingPort {
    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun info(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun warning(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }

    override fun recordException(tag: String, message: String, throwable: Throwable) {
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("LogTag", tag)
            crashlytics.setCustomKey("Message", message)
            crashlytics.recordException(throwable)
        }.onFailure {
            // Graceful degradation: If Crashlytics is unavailable, fallback to standard log
            Log.e("AndroidLoggingAdapter", "Crashlytics not available. Error: $message", throwable)
        }
    }
}
