package com.browntowndev.pocketcrew.domain.usecase.inference

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for InferenceLockManager.
 * Tests verify that the global inference lock correctly blocks concurrent on-device
 * inference while allowing BYOK (external API) inference to proceed.
 */
class InferenceLockManagerTest {

    private lateinit var manager: InferenceLockManagerImpl

    @BeforeEach
    fun setup() {
        manager = InferenceLockManagerImpl()
    }

    @Test
    fun `acquireLock returns true when no lock held`() = runTest {
        // When
        val result = manager.acquireLock(InferenceType.ON_DEVICE)

        // Then
        assertTrue(result)
    }

    @Test
    fun `acquireLock returns false when ON_DEVICE lock already held`() = runTest {
        // Given
        manager.acquireLock(InferenceType.ON_DEVICE)

        // When
        val result = manager.acquireLock(InferenceType.ON_DEVICE)

        // Then
        assertFalse(result)
    }

    @Test
    fun `acquireLock allows BYOK when ON_DEVICE lock held`() = runTest {
        // Given
        manager.acquireLock(InferenceType.ON_DEVICE)

        // When
        val result = manager.acquireLock(InferenceType.BYOK)

        // Then - BYOK should succeed even when ON_DEVICE is active
        assertTrue(result)
    }

    @Test
    fun `acquireLock allows ON_DEVICE when BYOK lock held`() = runTest {
        // Given
        manager.acquireLock(InferenceType.BYOK)

        // When
        val result = manager.acquireLock(InferenceType.ON_DEVICE)

        // Then - ON_DEVICE should succeed even when BYOK is active (first BYOK still running)
        assertTrue(result)
    }

    @Test
    fun `releaseLock unblocks after acquired`() = runTest {
        // Given
        manager.acquireLock(InferenceType.ON_DEVICE)

        // When
        manager.releaseLock()

        // Then - should be able to acquire again
        val result = manager.acquireLock(InferenceType.ON_DEVICE)
        assertTrue(result)
    }

    @Test
    fun `isInferenceBlocked emits true when ON_DEVICE lock acquired`() = runTest {
        // Given
        assertFalse(manager.isInferenceBlocked.first())

        // When
        manager.acquireLock(InferenceType.ON_DEVICE)

        // Then
        assertTrue(manager.isInferenceBlocked.first())
    }

    @Test
    fun `isInferenceBlocked emits false when lock released`() = runTest {
        // Given
        manager.acquireLock(InferenceType.ON_DEVICE)
        assertTrue(manager.isInferenceBlocked.first())

        // When
        manager.releaseLock()

        // Then
        assertFalse(manager.isInferenceBlocked.first())
    }

    @Test
    fun `isInferenceBlocked is false when only BYOK lock held`() = runTest {
        // Given
        manager.acquireLock(InferenceType.BYOK)

        // Then - BYOK doesn't block other inferences
        assertFalse(manager.isInferenceBlocked.first())
    }

    @Test
    fun `isInferenceBlocked is true when ON_DEVICE acquired after BYOK`() = runTest {
        // Given
        manager.acquireLock(InferenceType.BYOK)
        assertFalse(manager.isInferenceBlocked.first())

        // When
        manager.acquireLock(InferenceType.ON_DEVICE)

        // Then - now blocked because ON_DEVICE acquired
        assertTrue(manager.isInferenceBlocked.first())
    }

    @Test
    fun `concurrent acquire from multiple coroutines only first succeeds`() = runTest {
        // When - simulate concurrent acquire attempts
        val results = mutableListOf<Boolean>()

        // First call succeeds
        results.add(manager.acquireLock(InferenceType.ON_DEVICE))

        // Subsequent calls should fail
        results.add(manager.acquireLock(InferenceType.ON_DEVICE))
        results.add(manager.acquireLock(InferenceType.ON_DEVICE))

        // Then - only first should succeed
        assertEquals(1, results.count { it })
        assertEquals(2, results.count { !it })
    }

    @Test
    fun `multiple BYOK can be acquired simultaneously`() = runTest {
        // When - multiple BYOK acquisitions
        val result1 = manager.acquireLock(InferenceType.BYOK)
        val result2 = manager.acquireLock(InferenceType.BYOK)
        val result3 = manager.acquireLock(InferenceType.BYOK)

        // Then - all should succeed (BYOK doesn't block)
        assertTrue(result1)
        assertTrue(result2)
        assertTrue(result3)
        assertFalse(manager.isInferenceBlocked.first())
    }

    @Test
    fun `release decrements count correctly with multiple BYOK`() = runTest {
        // Given - multiple BYOK acquired
        manager.acquireLock(InferenceType.BYOK)
        manager.acquireLock(InferenceType.BYOK)
        manager.acquireLock(InferenceType.BYOK)

        // When - release one
        manager.releaseLock()

        // Then - still not blocked (2 remaining)
        assertFalse(manager.isInferenceBlocked.first())

        // When - release second
        manager.releaseLock()

        // Then - still not blocked (1 remaining)
        assertFalse(manager.isInferenceBlocked.first())

        // When - release third
        manager.releaseLock()

        // Then - now unblocked (0 remaining)
        assertFalse(manager.isInferenceBlocked.first())
    }
}
