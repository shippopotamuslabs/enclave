package com.browntowndev.enclave.core.data.sync

import com.browntowndev.enclave.domain.port.repository.EmbeddingProgressPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmbeddingProgressManager
    @Inject
    constructor(
        private val workManager: androidx.work.WorkManager,
        @com.browntowndev.enclave.domain.qualifier.ApplicationScope private val externalScope: kotlinx.coroutines.CoroutineScope,
    ) : EmbeddingProgressPort {
        private val _isReindexing = MutableStateFlow(false)
        override val isReindexing: StateFlow<Boolean> = _isReindexing.asStateFlow()

        private val _progress = MutableStateFlow(0f)
        override val progress: StateFlow<Float> = _progress.asStateFlow()

        init {
            externalScope.launch {
                workManager
                    .getWorkInfosForUniqueWorkFlow("enclave_embedding_regen")
                    .collect { workInfos ->
                        val workInfo = workInfos.firstOrNull()
                        if (workInfo != null) {
                            val state = workInfo.state
                            val active =
                                state == androidx.work.WorkInfo.State.RUNNING ||
                                    state == androidx.work.WorkInfo.State.ENQUEUED
                            _isReindexing.value = active

                            if (active) {
                                val progressData = workInfo.progress
                                val fraction = progressData.getFloat("fraction", 0f)
                                _progress.value = fraction
                            } else {
                                _progress.value = 0f
                            }
                        } else {
                            _isReindexing.value = false
                            _progress.value = 0f
                        }
                    }
            }
        }

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
