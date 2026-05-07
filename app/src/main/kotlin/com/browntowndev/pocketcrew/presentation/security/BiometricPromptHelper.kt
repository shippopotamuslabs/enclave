package com.browntowndev.pocketcrew.presentation.security

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CompletableDeferred
import javax.crypto.Cipher

class BiometricPromptHelper(private val activity: FragmentActivity) {

    suspend fun showPrompt(
        title: String,
        subtitle: String,
        cipher: Cipher? = null
    ): Result<BiometricPrompt.CryptoObject?> {
        val deferred = CompletableDeferred<Result<BiometricPrompt.CryptoObject?>>()
        
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                deferred.complete(Result.failure(Exception("Biometric error: $errString ($errorCode)")))
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                deferred.complete(Result.success(result.cryptoObject))
            }

            override fun onAuthenticationFailed() {
                // User failed but can try again, don't complete deferred yet unless max attempts reached
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setConfirmationRequired(false)
            .build()

        if (cipher != null) {
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        } else {
            prompt.authenticate(info)
        }

        return deferred.await()
    }
}
