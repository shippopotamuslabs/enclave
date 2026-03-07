package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider

/**
 * Fake implementation of TransactionProvider for testing.
 * Allows verifying transaction boundaries and simulating transaction failures.
 */
class FakeTransactionProvider : TransactionProvider {

    private var transactionCount = 0
    private var shouldThrowInTransaction = false

    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        if (shouldThrowInTransaction) {
            throw RuntimeException("Simulated transaction failure")
        }
        transactionCount++
        return block()
    }

    fun getTransactionCount(): Int = transactionCount

    fun verifyTransactionCalled(times: Int) {
        org.junit.jupiter.api.Assertions.assertEquals(times, transactionCount)
    }

    fun setShouldThrowInTransaction(shouldThrow: Boolean) {
        shouldThrowInTransaction = shouldThrow
    }

    fun reset() {
        transactionCount = 0
        shouldThrowInTransaction = false
    }
}

