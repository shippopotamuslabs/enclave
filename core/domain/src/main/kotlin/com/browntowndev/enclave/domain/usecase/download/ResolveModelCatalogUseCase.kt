package com.browntowndev.enclave.domain.usecase.download

import com.browntowndev.enclave.domain.model.download.ParsedModelCatalog
import com.browntowndev.enclave.domain.port.repository.ModelConfigFetcherPort
import javax.inject.Inject

class ResolveModelCatalogUseCase
    @Inject
    constructor(
        private val modelConfigFetcher: ModelConfigFetcherPort,
    ) {
        suspend operator fun invoke(): Result<ParsedModelCatalog> = modelConfigFetcher.getPersistedParsedCatalog()
    }
