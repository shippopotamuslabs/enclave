package com.browntowndev.enclave.domain.model.chat

/**
 * Shared regexes for stripping provider SDK exception dumps from user-facing copy.
 */
internal object ChatInferenceSdkMetadata {
    val EXCEPTION_PREFIX = Regex("""(?i)^exception=\w+\s*""")
    val MESSAGE_PREFIX = Regex("""(?i)^message=""")
    val STATUS_SUFFIX = Regex("""\s+status=\d+.*$""")
    val BODY_SUFFIX = Regex("""\s+body=\{.*$""")
    val PROVIDER_SUFFIX = Regex("""\(provider=[^)]+\)""")
    val MODEL_SUFFIX = Regex("""\bmodel=[^\s)]+""")
    val EXCEPTION_MESSAGE_PREFIX = Regex("""(?i)^exception=\w+\s+message=""")
    val STATUS_SUFFIX_INLINE = Regex("""\s+status=\d+.*""")

    val STACK_TRACE_LINE = Regex("""^\s*at\s+\S+\.\S+\(""")

    fun stripProviderDetailMetadata(message: String): String =
        message
            .replace(EXCEPTION_PREFIX, "")
            .replace(MESSAGE_PREFIX, "")
            .replace(STATUS_SUFFIX, "")
            .replace(BODY_SUFFIX, "")
            .replace(PROVIDER_SUFFIX, "")
            .replace(MODEL_SUFFIX, "")
            .trim()
            .trim('"')
}
