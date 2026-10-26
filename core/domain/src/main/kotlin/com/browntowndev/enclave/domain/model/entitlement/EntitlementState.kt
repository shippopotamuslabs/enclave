package com.browntowndev.enclave.domain.model.entitlement

/**
 * Subscription entitlement for cloud sync.
 *
 * Produced by the data layer from the override document and Stripe status; the sync gate
 * reads only this value, never billing internals. A listener or read error surfaces as
 * [Unknown], never [NotEntitled], so transient failures retry instead of stopping sync.
 */
sealed interface EntitlementState {
    data class Entitled(
        val expiryTimeMillis: Long? = null,
    ) : EntitlementState

    data object NotEntitled : EntitlementState

    data object Unknown : EntitlementState

    data object SignedOut : EntitlementState
}
