package com.browntowndev.enclave.core.data.sync

import com.browntowndev.enclave.domain.port.repository.EmbeddingProgressPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmbeddingProgressManager
    @Inject
    constructor() : EmbeddingProgressPort {
        private val _isReindexing = MutableStateFlow(false)
        override val isReindexing: StateFlow<Boolean> = _isReindexing.asStateFlow()

        private val _progress = MutableStateFlow(0f)
        override val progress: StateFlow<Float> = _progress.asStateFlow()

        fun setReindexing(active: Boolean) {
            _isReindexing.value = active
            if (!active) _progress.value = 0f
        }

        fun updateProgress(
            current: Int,
            total: Int,
        ) {
            if (total > 0) {
                _progress.value = current.toFloat() / total.toFloat()
            }
        }
    }
