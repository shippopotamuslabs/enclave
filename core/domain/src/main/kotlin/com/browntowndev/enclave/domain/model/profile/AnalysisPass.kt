package com.browntowndev.enclave.domain.model.profile

enum class AnalysisPass(val index: Int, val displayName: String) {
    EXTRACTION(0, "Extracting evidence"),
    CLUSTERING(1, "Clustering patterns"),
    SYNTHESIS(2, "Synthesizing profile"),
    CRITIQUE(3, "Self-critique & revision"),
    ;

    companion object {
        val ALL = entries.sortedBy { it.index }
        fun fromIndex(index: Int) = ALL.getOrNull(index)
    }
}
