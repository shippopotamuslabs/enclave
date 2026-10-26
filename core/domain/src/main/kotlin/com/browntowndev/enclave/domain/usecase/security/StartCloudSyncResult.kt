package com.browntowndev.enclave.domain.usecase.security

/**
 * Outcome of [StartCloudSyncListeningUseCase]. Only [Started] begins the live listener;
 * [SubscriptionRequired] stops cleanly without retry churn, and [Failed] preserves the
 * previous retryable-error behavior for transient failures.
 */
sealed interface StartCloudSyncResult {
    data object Started : StartCloudSyncResult

    data object SubscriptionRequired : StartCloudSyncResult

    data class Failed(
        val cause: Throwable,
    ) : StartCloudSyncResult
}
