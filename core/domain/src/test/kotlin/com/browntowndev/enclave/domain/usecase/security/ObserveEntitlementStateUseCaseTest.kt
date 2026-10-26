package com.browntowndev.enclave.domain.usecase.security

import app.cash.turbine.test
import com.browntowndev.enclave.core.testing.FakeEntitlementPort
import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ObserveEntitlementStateUseCaseTest {
    @Test
    fun `invoke passes through entitlement state updates`() =
        runTest {
            val fake = FakeEntitlementPort()
            val useCase = ObserveEntitlementStateUseCase(fake)

            useCase().test {
                assertEquals(EntitlementState.Unknown, awaitItem())

                fake.setEntitlementState(EntitlementState.Entitled())
                assertEquals(EntitlementState.Entitled(), awaitItem())

                fake.setEntitlementState(EntitlementState.NotEntitled)
                assertEquals(EntitlementState.NotEntitled, awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }
}
