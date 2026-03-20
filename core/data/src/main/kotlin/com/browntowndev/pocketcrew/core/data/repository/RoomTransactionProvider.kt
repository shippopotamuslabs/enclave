package com.browntowndev.pocketcrew.core.data.repository

import androidx.room.withTransaction
import com.browntowndev.pocketcrew.core.data.local.PocketCrewDatabase
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room implementation of TransactionProvider.
 * Uses Room's withTransaction to ensure atomic execution of database operations.
 *
 * This implementation wraps the Room database's transaction mechanism,
 * providing a clean abstraction that the domain layer can use without
 * knowing about Room-specific APIs.
 */
@Singleton
class RoomTransactionProvider @Inject constructor(
    private val database: PocketCrewDatabase
) : TransactionProvider {

    /**
     * Executes the given suspend function within a Room transaction.
     * If any exception is thrown, the transaction will be rolled back automatically.
     *
     * @param T The return type of the block
     * @param block The suspend function to execute within the transaction
     * @return The result of the block
     */
    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return database.withTransaction {
            block()
        }
    }
}
