package com.browntowndev.pocketcrew.domain.port.repository

/**
 * Abstraction for database transactions.
 *
 * This interface decouples the domain layer from the specific database implementation
 * (Room, SQLDelight, etc.). By injecting this abstraction, use cases can execute
 * multiple repository operations atomically without knowing the underlying transaction
 * mechanism.
 *
 * Why this abstraction at the domain level:
 * - Business logic may require atomic operations spanning multiple repository calls
 * - Keeps the domain layer clean of data layer specifics
 * - Enables easier testing with mock implementations
 * - Future database migrations become simpler
 */
interface TransactionProvider {
    /**
     * Executes a block of code within a database transaction.
     * If any exception is thrown within the block, the transaction will be rolled back.
     *
     * @param T The return type of the block
     * @param block The suspend function to execute within the transaction
     * @return The result of the block
     */
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
