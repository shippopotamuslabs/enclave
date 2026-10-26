package com.browntowndev.enclave.core.data.entitlement

import android.content.Context
import com.browntowndev.enclave.core.data.FirebaseAvailability
import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import com.browntowndev.enclave.domain.port.repository.EntitlementPort
import com.browntowndev.enclave.domain.qualifier.ApplicationScope
import com.browntowndev.enclave.domain.util.Clock
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore-backed [EntitlementPort]. Swaps Firestore snapshot listeners on
 * `users/{uid}/entitlement/stripe` and `users/{uid}/entitlement/override` whenever the
 * FirebaseAuth user changes, and combines the latest snapshots into [EntitlementState].
 *
 * Listener or read errors surface as [EntitlementState.Unknown] (never
 * [EntitlementState.NotEntitled]) and the listeners stay attached, so the next good
 * snapshot recovers. Dark builds (no config-backed Firebase app) stay
 * [EntitlementState.SignedOut] without touching Firestore.
 */
@Singleton
class FirebaseEntitlementPort
    @Inject
    constructor(
        private val firebaseAuth: FirebaseAuth,
        private val firestore: FirebaseFirestore,
        @ApplicationContext private val context: Context,
        @ApplicationScope private val externalScope: CoroutineScope,
        private val clock: Clock,
        private val loggingPort: LoggingPort,
    ) : EntitlementPort {
        companion object {
            private const val TAG = "EntitlementPort"
        }

        private val _entitlementState = MutableStateFlow<EntitlementState>(EntitlementState.Unknown)
        override val entitlementState: StateFlow<EntitlementState> = _entitlementState.asStateFlow()

        private var stripeRegistration: ListenerRegistration? = null
        private var overrideRegistration: ListenerRegistration? = null
        private var stripeLoaded = false
        private var overrideLoaded = false
        private var stripeData: Map<String, Any?>? = null
        private var overrideData: Map<String, Any?>? = null

        init {
            if (!FirebaseAvailability.isConfigBacked(context)) {
                _entitlementState.value = EntitlementState.SignedOut
            } else {
                externalScope.launch {
                    firebaseAuth.addAuthStateListener { auth ->
                        onAuthChanged(auth.currentUser?.uid)
                    }
                }
            }
        }

        private fun onAuthChanged(uid: String?) {
            stripeRegistration?.remove()
            overrideRegistration?.remove()
            stripeRegistration = null
            overrideRegistration = null
            stripeLoaded = false
            overrideLoaded = false
            stripeData = null
            overrideData = null
            if (uid == null) {
                _entitlementState.value = EntitlementState.SignedOut
                return
            }
            val entitlement = firestore.collection("users").document(uid).collection("entitlement")
            stripeRegistration =
                entitlement.document(STRIPE_DOC_ID).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        loggingPort.warning(TAG, "Stripe entitlement listener error; staying recoverable")
                        _entitlementState.value = EntitlementState.Unknown
                        return@addSnapshotListener
                    }
                    stripeData = snapshot?.data
                    stripeLoaded = true
                    recompute()
                }
            overrideRegistration =
                entitlement.document(OVERRIDE_DOC_ID).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        loggingPort.warning(TAG, "Override entitlement listener error; staying recoverable")
                        _entitlementState.value = EntitlementState.Unknown
                        return@addSnapshotListener
                    }
                    overrideData = snapshot?.data
                    overrideLoaded = true
                    recompute()
                }
        }

        private fun recompute() {
            if (!stripeLoaded || !overrideLoaded) return
            _entitlementState.value =
                mapEntitlementState(
                    stripeData = stripeData,
                    overrideData = overrideData,
                    nowMillis = clock.currentTimeMillis(),
                )
        }
    }
