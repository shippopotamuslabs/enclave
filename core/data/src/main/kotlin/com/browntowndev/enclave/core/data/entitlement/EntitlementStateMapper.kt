package com.browntowndev.enclave.core.data.entitlement

import com.browntowndev.enclave.domain.model.entitlement.EntitlementState

internal const val STRIPE_DOC_ID = "stripe"
internal const val OVERRIDE_DOC_ID = "override"
internal const val FIELD_FREE_ACCESS = "freeAccess"
internal const val FIELD_ACTIVE = "active"
internal const val FIELD_EXPIRY_TIME_MILLIS = "expiryTimeMillis"

/**
 * Pure snapshot-to-state mapping for the entitlement gate. Mirrors the server rules
 * (`firestore.rules` `isOverrideEntitled` / `isStripeEntitled`): override `freeAccess`
 * grants unconditionally; otherwise Stripe `active` must hold and `expiryTimeMillis`,
 * when present, must be in the future. Missing docs or fields deny (never grant).
 *
 * Kept free of Firestore types so unit tests can drive it with plain maps.
 */
internal fun mapEntitlementState(
    stripeData: Map<String, Any?>?,
    overrideData: Map<String, Any?>?,
    nowMillis: Long,
): EntitlementState {
    if (overrideData?.get(FIELD_FREE_ACCESS) == true) {
        return EntitlementState.Entitled(
            expiryTimeMillis = (stripeData?.get(FIELD_EXPIRY_TIME_MILLIS) as? Number)?.toLong(),
        )
    }
    if (stripeData?.get(FIELD_ACTIVE) == true) {
        val expiry = (stripeData[FIELD_EXPIRY_TIME_MILLIS] as? Number)?.toLong()
        if (expiry == null || expiry > nowMillis) {
            return EntitlementState.Entitled(expiryTimeMillis = expiry)
        }
    }
    return EntitlementState.NotEntitled
}
