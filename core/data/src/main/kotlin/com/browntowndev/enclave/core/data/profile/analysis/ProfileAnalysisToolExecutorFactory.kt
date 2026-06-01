package com.browntowndev.enclave.core.data.profile.analysis

import com.browntowndev.enclave.core.data.local.ProfileCorpusDao
import com.browntowndev.enclave.core.data.local.ProfileFindingsDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileAnalysisToolExecutorFactory
    @Inject
    constructor(
        private val corpusDao: ProfileCorpusDao,
        private val findingsDao: ProfileFindingsDao,
    ) {
        fun create(corpusRunId: String): ProfileAnalysisToolExecutor =
            ProfileAnalysisToolExecutor(
                corpusRunId = corpusRunId,
                corpusDao = corpusDao,
                findingsDao = findingsDao,
            )
    }
