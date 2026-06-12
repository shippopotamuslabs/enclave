package com.browntowndev.enclave.feature.inference.compaction

import android.content.Context
import com.browntowndev.enclave.domain.port.inference.CompactionSummarizerPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import com.browntowndev.enclave.feature.inference.utilityonnx.Lfm2UtilityOnnxRunner
import com.browntowndev.enclave.feature.inference.utilityonnx.UtilityOnnxLoadCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnnxCompactionSummarizer
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
        utilityModelFilePort: UtilityModelFilePort,
        loadCoordinator: UtilityOnnxLoadCoordinator,
    ) : CompactionSummarizerPort {
        private val runner =
            Lfm2UtilityOnnxRunner(
                context = context,
                utilityModelFilePort = utilityModelFilePort,
                loadCoordinator = loadCoordinator,
                logTag = TAG,
            )

        override suspend fun isModelInstalled(): Boolean = runner.isModelInstalled()

        override suspend fun summarize(prompt: String): String? =
            runner.generate(configAssetPath = CONFIG_ASSET_PATH, userMessage = prompt)

        private companion object {
            private const val TAG = "OnnxCompactionSum"
            private const val CONFIG_ASSET_PATH = "models/compaction_summarizer/model_config.json"
        }
    }
