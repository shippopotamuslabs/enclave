package com.browntowndev.pocketcrew.core.data.auth

import androidx.credentials.CredentialManager
import com.browntowndev.pocketcrew.domain.port.auth.AuthState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirebaseGoogleAuthManagerTest {

    private val firebaseAuth = mockk<FirebaseAuth>(relaxed = true)
    private val credentialManager = mockk<CredentialManager>(relaxed = true)
    private val testScope = TestScope(UnconfinedTestDispatcher())
    
    private lateinit var authManager: FirebaseGoogleAuthManager

    @BeforeEach
    fun setup() {
        authManager = FirebaseGoogleAuthManager(
            context = mockk(relaxed = true),
            firebaseAuth = firebaseAuth,
            credentialManager = credentialManager,
            externalScope = testScope
        )
    }

    @Test
    fun `authState initially reflects firebase user`() = runTest {
        val listenerSlot = slot<FirebaseAuth.AuthStateListener>()
        every { firebaseAuth.addAuthStateListener(capture(listenerSlot)) } returns Unit
        
        val mockUser = mockk<FirebaseUser> {
            every { uid } returns "test-uid"
            every { displayName } returns "Test User"
            every { email } returns "test@example.com"
            every { photoUrl } returns null
        }
        every { firebaseAuth.currentUser } returns mockUser
        
        // Re-init to trigger listener registration
        authManager = FirebaseGoogleAuthManager(
            context = mockk(relaxed = true),
            firebaseAuth = firebaseAuth,
            credentialManager = credentialManager,
            externalScope = testScope
        )

        // Manually trigger the captured listener
        listenerSlot.captured.onAuthStateChanged(firebaseAuth)

        val state = authManager.authState.value
        assertTrue(state is AuthState.Authenticated)
        assertEquals("test-uid", (state as AuthState.Authenticated).user.uid)
    }

    @Test
    fun `signOut calls firebase signOut`() = runTest {
        authManager.signOut()
        verify { firebaseAuth.signOut() }
    }
}
