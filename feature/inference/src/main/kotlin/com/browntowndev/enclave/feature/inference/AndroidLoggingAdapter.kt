package com.browntowndev.enclave.feature.inference

import android.util.Log
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [LoggingPort] that uses Android's Log utility.
 * This adapter lives in the inference layer where Android SDK is available.
 */
@Singleton
class AndroidLoggingAdapter
    @Inject
    constructor() : LoggingPort {
        override fun debug(
            tag: String,
            message: String,
        ) {
            Log.d(tag, message)
        }

        override fun info(
            tag: String,
            message: String,
        ) {
            Log.i(tag, message)
        }

        override fun warning(
            tag: String,
            message: String,
        ) {
            Log.w(tag, message)
        }

        override fun error(
            tag: String,
            message: String,
            throwable: Throwable?,
        ) {
            Log.e(tag, message, throwable)
            if (throwable != null) {
                runCatching {
                    val crashlytics = FirebaseCrashlytics.getInstance()
                    crashlytics.log("$tag: $message")
                    crashlytics.recordException(throwable)
                }.onFailure {
                    Log.e("AndroidLoggingAdapter", "Crashlytics not available. Error: $message", throwable)
                }
            }
        }
    }
