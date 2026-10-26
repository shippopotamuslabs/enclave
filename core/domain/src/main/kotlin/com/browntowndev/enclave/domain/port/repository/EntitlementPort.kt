package com.browntowndev.enclave.domain.port.repository

import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import kotlinx.coroutines.flow.StateFlow

/**
 * Source of truth for the user's subscription entitlement. The data-layer implementation
 * derives this from the override document and Stripe status; read errors surface as
 * [EntitlementState.Unknown], never [EntitlementState.NotEntitled].
 */
interface EntitlementPort {
    val entitlementState: StateFlow<EntitlementState>
}
