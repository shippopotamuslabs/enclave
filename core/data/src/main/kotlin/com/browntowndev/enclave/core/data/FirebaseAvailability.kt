package com.browntowndev.enclave.core.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Dark-build guard for Firebase (ENC-212).
 *
 * When `app/google-services.json` is present, the Google Services plugin
 * injects the config and `FirebaseInitProvider` initializes the default
 * [FirebaseApp] before Hilt runs. [ensureFirebaseApp] is then a no-op and
 * every Firebase SDK call behaves exactly as before.
 *
 * When the file is absent (a bare clone), no default app exists and any
 * `FirebaseAuth.getInstance()` / `FirebaseFirestore.getInstance()` call
 * throws `IllegalStateException`, which would crash Hilt construction of
 * the auth and sync graphs at startup. Installing a placeholder app here,
 * at the provider layer, keeps those SDK handles constructible while the
 * backend stays unreachable: there is no signed-in user, so auth reports
 * signed-out and every sync entry point already no-ops on a null user id.
 * The placeholder never talks to a real project; its project id and app id
 * name no Firebase project.
 */
object FirebaseAvailability {
    private const val DARK_PROJECT_ID = "enclave-dark-no-project"
    private const val DARK_APPLICATION_ID = "1:000000000000:android:0000000000000000000000"
    private const val DARK_API_KEY = "enclave-dark-placeholder-not-a-real-key"

    /** Returns true when the default [FirebaseApp] came from google-services config, not the placeholder. */
    fun isConfigBacked(context: Context): Boolean =
        runCatching {
            FirebaseApp.getApps(context).isNotEmpty() &&
                FirebaseApp.getInstance().options.projectId != DARK_PROJECT_ID
        }.getOrDefault(false)

    /**
     * Installs the placeholder default app when no config-backed app exists.
     * Returns true when the resulting default app is config-backed, false when
     * the placeholder was installed (Firebase dark) or installation failed.
     */
    fun ensureFirebaseApp(context: Context): Boolean {
        if (isConfigBacked(context)) return true
        if (FirebaseApp.getApps(context).isNotEmpty()) return false
        val options =
            FirebaseOptions
                .Builder()
                .setProjectId(DARK_PROJECT_ID)
                .setApplicationId(DARK_APPLICATION_ID)
                .setApiKey(DARK_API_KEY)
                .build()
        runCatching { FirebaseApp.initializeApp(context, options) }
        return isConfigBacked(context)
    }
}
