package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import com.browntowndev.enclave.domain.port.repository.EntitlementPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [EntitlementPort] fake. Defaults to [EntitlementState.Unknown]. */
class FakeEntitlementPort(
    initialState: EntitlementState = EntitlementState.Unknown,
) : EntitlementPort {
    private val mutableEntitlementState = MutableStateFlow(initialState)
    override val entitlementState: StateFlow<EntitlementState> = mutableEntitlementState

    fun setEntitlementState(state: EntitlementState) {
        mutableEntitlementState.value = state
    }
}
