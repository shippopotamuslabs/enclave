package com.browntowndev.enclave.feature.studio

/**
 * One-shot moderation events consumed by [StudioScreen] to drive snackbars with
 * a "GOT IT WRONG?" action button. Emitted via `Channel<ModerationEvent>` from
 * the ViewModel and coordinators.
 */
sealed interface ModerationEvent {
    /**
     * Media generation was blocked by moderation.
     *
     * @param prompt The prompt that was blocked, preserved so the user can report it as a false
     *  positive via [MultimodalViewModel.reportBlockedPrompt].
     */
    data class Blocked(val prompt: String) : ModerationEvent

    /** A moderation false-positive report was submitted to Firestore. */
    data object ReportSubmitted : ModerationEvent
}
