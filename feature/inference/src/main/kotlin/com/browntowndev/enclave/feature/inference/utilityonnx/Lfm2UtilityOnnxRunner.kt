package com.browntowndev.enclave.feature.inference.utilityonnx

import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import com.browntowndev.enclave.feature.inference.titlegen.Lfm2BpeTokenizer
import com.browntowndev.enclave.feature.inference.titlegen.TitleGenAssetConfig
import com.browntowndev.enclave.feature.inference.titlegen.createSession
import com.browntowndev.enclave.feature.inference.titlegen.runTitleGeneration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

internal class Lfm2UtilityOnnxRunner(
    private val context: Context,
    private val utilityModelFilePort: UtilityModelFilePort,
    private val loadCoordinator: UtilityOnnxLoadCoordinator,
    private val logTag: String,
) {
    private val cacheMutex = Mutex()

    @Volatile
    private var cachedTokenizer: Lfm2BpeTokenizer? = null

    private val configCache = mutableMapOf<String, TitleGenAssetConfig>()

    suspend fun isModelInstalled(): Boolean = resolveLfm2UtilityArtifacts(utilityModelFilePort) != null

    suspend fun generate(
        configAssetPath: String,
        userMessage: String,
    ): String? =
        withContext(Dispatchers.IO) {
            if (userMessage.isBlank()) return@withContext null
            loadCoordinator.withExclusiveLoad {
                try {
                    val artifacts = resolveLfm2UtilityArtifacts(utilityModelFilePort) ?: return@withExclusiveLoad null
                    val config = getConfig(configAssetPath)
                    val tokenizer = getTokenizer(artifacts.tokenizerPath)
                    val environment = OrtEnvironment.getEnvironment()
                    val session = createSession(environment = environment, modelPath = artifacts.graphPath)
                    try {
                        val result =
                            runTitleGeneration(
                                session = session,
                                tokenizer = tokenizer,
                                config = config,
                                userMessage = userMessage,
                            )
                        result.rawDecode?.trim()?.takeIf { it.isNotBlank() }
                    } finally {
                        session.close()
                    }
                } catch (e: Exception) {
                    Log.w(logTag, "Utility ONNX generation failed", e)
                    null
                }
            }
        }

    private suspend fun getConfig(configAssetPath: String): TitleGenAssetConfig =
        configCache[configAssetPath]
            ?: cacheMutex.withLock {
                configCache[configAssetPath]
                    ?: TitleGenAssetConfig.parse(readAssetText(configAssetPath))
                        .also { configCache[configAssetPath] = it }
            }

    private suspend fun getTokenizer(tokenizerPath: String): Lfm2BpeTokenizer =
        cachedTokenizer ?: cacheMutex.withLock {
            cachedTokenizer ?: Lfm2BpeTokenizer.fromTokenizerJson(File(tokenizerPath).readText())
                .also { cachedTokenizer = it }
        }

    private fun readAssetText(path: String): String =
        context.assets.open(path).bufferedReader().use { reader -> reader.readText() }
}
