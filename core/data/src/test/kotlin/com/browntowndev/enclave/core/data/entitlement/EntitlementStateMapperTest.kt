package com.browntowndev.enclave.core.data.entitlement

import com.browntowndev.enclave.domain.model.entitlement.EntitlementState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EntitlementStateMapperTest {
    private val now = 1_700_000_000_000L
    private val future = 1_800_000_000_000L
    private val past = 1_600_000_000_000L

    @Test
    fun `override freeAccess grants entitlement with stripe expiry`() {
        assertEquals(
            EntitlementState.Entitled(expiryTimeMillis = future),
            mapEntitlementState(
                stripeData = mapOf("active" to false, "expiryTimeMillis" to future),
                overrideData = mapOf("freeAccess" to true),
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `override freeAccess grants entitlement without stripe doc`() {
        assertEquals(
            EntitlementState.Entitled(expiryTimeMillis = null),
            mapEntitlementState(
                stripeData = null,
                overrideData = mapOf("freeAccess" to true),
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `active stripe without expiry grants entitlement`() {
        assertEquals(
            EntitlementState.Entitled(expiryTimeMillis = null),
            mapEntitlementState(
                stripeData = mapOf("active" to true),
                overrideData = null,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `active stripe with future expiry grants entitlement`() {
        assertEquals(
            EntitlementState.Entitled(expiryTimeMillis = future),
            mapEntitlementState(
                stripeData = mapOf("active" to true, "expiryTimeMillis" to future),
                overrideData = mapOf("freeAccess" to false),
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `active stripe with past expiry denies entitlement`() {
        assertEquals(
            EntitlementState.NotEntitled,
            mapEntitlementState(
                stripeData = mapOf("active" to true, "expiryTimeMillis" to past),
                overrideData = null,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `active stripe with expiry exactly now denies entitlement`() {
        assertEquals(
            EntitlementState.NotEntitled,
            mapEntitlementState(
                stripeData = mapOf("active" to true, "expiryTimeMillis" to now),
                overrideData = null,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `inactive stripe denies entitlement`() {
        assertEquals(
            EntitlementState.NotEntitled,
            mapEntitlementState(
                stripeData = mapOf("active" to false, "expiryTimeMillis" to future),
                overrideData = null,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `missing docs deny entitlement`() {
        assertEquals(
            EntitlementState.NotEntitled,
            mapEntitlementState(
                stripeData = null,
                overrideData = null,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `empty docs deny entitlement`() {
        assertEquals(
            EntitlementState.NotEntitled,
            mapEntitlementState(
                stripeData = emptyMap(),
                overrideData = emptyMap(),
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `non boolean freeAccess denies entitlement`() {
        assertEquals(
            EntitlementState.NotEntitled,
            mapEntitlementState(
                stripeData = null,
                overrideData = mapOf("freeAccess" to "yes"),
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `double expiry converts to long`() {
        assertEquals(
            EntitlementState.Entitled(expiryTimeMillis = future),
            mapEntitlementState(
                stripeData = mapOf("active" to true, "expiryTimeMillis" to future.toDouble()),
                overrideData = null,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `non numeric expiry counts as absent`() {
        assertEquals(
            EntitlementState.Entitled(expiryTimeMillis = null),
            mapEntitlementState(
                stripeData = mapOf("active" to true, "expiryTimeMillis" to "tomorrow"),
                overrideData = null,
                nowMillis = now,
            ),
        )
    }
}
