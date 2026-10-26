package com.browntowndev.enclave.domain.exception

/**
 * Domain error raised when cloud sync is attempted without an active subscription.
 *
 * Thrown by the data layer when Firestore or Storage denies access for a non-subscriber;
 * callers map it to a stop-sync (rather than retry) decision.
 */
class SubscriptionRequiredException(
    message: String = "Cloud sync requires an active subscription",
) : Exception(message)
