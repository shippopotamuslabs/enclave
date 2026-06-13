package com.browntowndev.enclave.feature.inference.utilityonnx

import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort

/**
 * Resolved on-disk paths for the shared LFM2.5 utility ONNX bundle (title gen + compaction).
 *
 * The decoder [dataPath] is required so checksum validation proves external weights exist;
 * ORT loads weights from the sidecar adjacent to [graphPath].
 */
internal data class Lfm2UtilityArtifacts(
    val graphPath: String,
    val dataPath: String,
    val tokenizerPath: String,
)

internal suspend fun resolveLfm2UtilityArtifacts(
    utilityModelFilePort: UtilityModelFilePort,
): Lfm2UtilityArtifacts? {
    val graphPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_ENCODER)
    val dataPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_DECODER)
    val tokenizerPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_TOKENIZER)
    if (graphPath == null || dataPath == null || tokenizerPath == null) {
        return null
    }
    return Lfm2UtilityArtifacts(
        graphPath = graphPath,
        dataPath = dataPath,
        tokenizerPath = tokenizerPath,
    )
}
