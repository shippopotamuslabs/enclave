package com.browntowndev.enclave.domain.usecase.chat

internal const val TRANSCRIPT_SEGMENT_SEPARATOR = "\n\n"

internal fun StringBuilder.endsWithNewline(): Boolean =
    isNotEmpty() && last() == '\n'

internal fun StringBuilder.appendSegmentSeparatorIfNeeded() {
    if (isNotEmpty() && !endsWithNewline()) {
        append(TRANSCRIPT_SEGMENT_SEPARATOR)
    }
}
