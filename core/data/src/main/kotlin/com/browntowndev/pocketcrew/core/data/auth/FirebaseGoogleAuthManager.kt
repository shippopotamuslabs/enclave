package com.browntowndev.pocketcrew.core.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.browntowndev.pocketcrew.domain.port.auth.AuthManager
import com.browntowndev.pocketcrew.domain.port.auth.AuthState
import com.browntowndev.pocketcrew.domain.port.auth.UserProfile
import com.browntowndev.pocketcrew.domain.qualifier.ApplicationScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseGoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    @ApplicationScope private val externalScope: CoroutineScope
) : AuthManager {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        externalScope.launch {
            firebaseAuth.addAuthStateListener { auth ->
                val user = auth.currentUser
                if (user != null) {
                    _authState.value = AuthState.Authenticated(
                        UserProfile(
                            uid = user.uid,
                            displayName = user.displayName,
                            email = user.email,
                            photoUrl = user.photoUrl?.toString()
                        )
                    )
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            }
        }
    }

    override suspend fun signInWithGoogle(): Result<UserProfile> {
        return try {
            val webClientId = context.getString(
                context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            )

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is GoogleIdTokenCredential) {
                val firebaseCredential = GoogleAuthProvider.getCredential(credential.idToken, null)
                val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
                val user = authResult.user ?: throw Exception("Firebase sign in failed")
                
                Result.success(
                    UserProfile(
                        uid = user.uid,
                        displayName = user.displayName,
                        email = user.email,
                        photoUrl = user.photoUrl?.toString()
                    )
                )
            } else {
                Result.failure(Exception("Unexpected credential type: ${credential.type}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return try {
            firebaseAuth.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
