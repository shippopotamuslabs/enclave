package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.port.repository.ExtractedUrlTrackerPort

/** In-memory [ExtractedUrlTrackerPort] fake (ENC-28). */
class FakeExtractedUrlTracker : ExtractedUrlTrackerPort {
    private val backing = mutableSetOf<String>()
    override val urls: Set<String> get() = backing.toSet()

    var clearCount = 0
        private set

    override fun add(url: String) {
        backing += url
    }

    override fun clear() {
        clearCount++
        backing.clear()
    }
}
