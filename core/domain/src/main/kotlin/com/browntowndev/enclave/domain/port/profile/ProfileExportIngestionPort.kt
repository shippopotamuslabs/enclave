package com.browntowndev.enclave.domain.port.profile

import com.browntowndev.enclave.domain.model.profile.ParseHealthReport

interface ProfileExportIngestionPort {
    /**
     * Unzip each URI, detect provider, parse conversations, persist to corpus.
     * Returns one [ParseHealthReport] per parsed export entry.
     */
    suspend fun ingestZipFiles(
        uris: List<String>,
        corpusRunId: String,
    ): List<ParseHealthReport>

    /** Delete corpus conversations + messages and remove any temp files. */
    suspend fun clearCorpus(corpusRunId: String)

    /**
     * Delete ALL data for a run: corpus conversations, messages, findings, analysis run state,
     * and temp files. Call this after the user approves and saves the profile.
     */
    suspend fun clearAll(corpusRunId: String)
}
