package com.browntowndev.pocketcrew.core.data.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.browntowndev.pocketcrew.core.data.tokenizer.KotlinWordPieceTokenizer
import com.browntowndev.pocketcrew.domain.model.config.UtilityType
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.repository.UtilityModelFilePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class MiniLMEmbeddingEngine @Inject constructor(
    private val utilityModelFilePort: UtilityModelFilePort
) : EmbeddingEnginePort {

    private val mutex = Mutex()
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var tokenizer: KotlinWordPieceTokenizer? = null

    private suspend fun ensureInitialized() = mutex.withLock {
        if (session != null) return@withLock

        val modelPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.ONNX_MODEL)
            ?: throw IllegalStateException("ONNX model path not resolved")
        val vocabPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.ONNX_VOCAB)
            ?: throw IllegalStateException("ONNX vocab path not resolved")

        val modelFile = File(modelPath)
        val vocabFile = File(vocabPath)

        if (!modelFile.exists() || !vocabFile.exists()) {
            throw IllegalStateException("ONNX model or vocab file missing: model=${modelFile.exists()}, vocab=${vocabFile.exists()}")
        }

        env = OrtEnvironment.getEnvironment()
        session = env?.createSession(modelPath, OrtSession.SessionOptions())
        tokenizer = KotlinWordPieceTokenizer(vocabFile)
    }

    override suspend fun getEmbedding(text: String): FloatArray = withContext(Dispatchers.Default) {
        ensureInitialized()

        val tokenization = tokenizer?.tokenize(text) ?: throw IllegalStateException("Tokenizer not initialized")
        val env = env ?: throw IllegalStateException("ONNX Environment not initialized")
        val session = session ?: throw IllegalStateException("ONNX Session not initialized")

        val shape = longArrayOf(1, 256)
        val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenization.inputIds), shape)
        val attentionMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenization.attentionMask), shape)
        val tokenTypeIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenization.tokenTypeIds), shape)

        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor,
            "token_type_ids" to tokenTypeIdsTensor
        )

        session.run(inputs).use { result ->
            val outputTensor = result.get(0) as OnnxTensor
            val floatBuffer = outputTensor.floatBuffer
            
            // Mean Pooling
            val sums = FloatArray(384) { 0.0f }
            var validTokenCount = 0
            for (i in 0 until 256) {
                if (tokenization.attentionMask[i] == 1L) {
                    validTokenCount++
                    for (j in 0 until 384) {
                        sums[j] += floatBuffer.get(i * 384 + j)
                    }
                }
            }

            if (validTokenCount > 0) {
                for (j in 0 until 384) {
                    sums[j] /= validTokenCount.toFloat()
                }
            }

            // L2 Normalization
            var normSquare = 0.0f
            for (j in 0 until 384) {
                normSquare += sums[j] * sums[j]
            }
            val norm = sqrt(normSquare)
            if (norm > 0) {
                for (j in 0 until 384) {
                    sums[j] /= norm
                }
            }

            sums
        }
    }

    override suspend fun close() = mutex.withLock {
        session?.close()
        env?.close()
        session = null
        env = null
        tokenizer = null
    }
}
