package com.browntowndev.enclave.domain.usecase.security

import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import com.browntowndev.enclave.domain.port.repository.EntitlementPort
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveEntitlementStateUseCase
    @Inject
    constructor(
        private val entitlementPort: EntitlementPort,
    ) {
        operator fun invoke(): StateFlow<EntitlementState> = entitlementPort.entitlementState
    }
