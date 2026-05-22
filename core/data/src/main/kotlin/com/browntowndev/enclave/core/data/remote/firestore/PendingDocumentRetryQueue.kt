package com.browntowndev.enclave.core.data.remote.firestore

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Buffer for Firestore document upserts that fail their first apply because of a missing
 * parent row (Room foreign-key violation, `SQLiteConstraintException`). Firestore snapshot
 * listeners deliver collection batches independently, so a child collection ([default_models],
 * [local_model_configs], [api_model_configs]) can arrive before the parent rows it
 * references. This queue holds the failed actions, retries them after each subsequent
 * successful upsert, and drops entries that never resolve within [maxAttempts] passes.
 */
internal class PendingDocumentRetryQueue(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val onDrop: suspend (key: String, cause: Throwable) -> Unit,
) {
    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 10
    }

    private data class Entry(
        val key: String,
        var attempts: Int,
        val action: suspend () -> Unit,
    )

    private val mutex = Mutex()
    private val pending = mutableListOf<Entry>()

    suspend fun enqueue(
        key: String,
        action: suspend () -> Unit,
    ) {
        mutex.withLock {
            pending.removeAll { it.key == key }
            pending.add(Entry(key = key, attempts = 1, action = action))
        }
    }

    /**
     * Re-runs every queued action. Loops until a full pass produces no successful entry,
     * so newly-arrived parents unblock multiple children in one drain. Entries that throw
     * [SQLiteConstraintException] stay queued (attempts++); other exceptions and exhausted
     * entries are dropped via [onDrop].
     */
    suspend fun drain() {
        while (true) {
            val snapshot = mutex.withLock { pending.toList() }
            if (snapshot.isEmpty()) return

            var madeProgress = false
            for (entry in snapshot) {
                val outcome = runEntry(entry)
                when (outcome) {
                    EntryOutcome.SUCCESS -> {
                        mutex.withLock { pending.removeAll { it.key == entry.key } }
                        madeProgress = true
                    }
                    EntryOutcome.FK_RETRY -> {
                        val drop =
                            mutex.withLock {
                                val current = pending.firstOrNull { it.key == entry.key } ?: return@withLock false
                                current.attempts += 1
                                current.attempts >= maxAttempts
                            }
                        if (drop) {
                            mutex.withLock { pending.removeAll { it.key == entry.key } }
                            onDrop(entry.key, SQLiteConstraintException("FK never resolved after $maxAttempts attempts"))
                        }
                    }
                    EntryOutcome.PERMANENT_FAIL -> {
                        mutex.withLock { pending.removeAll { it.key == entry.key } }
                    }
                }
            }
            if (!madeProgress) return
        }
    }

    suspend fun size(): Int = mutex.withLock { pending.size }

    private suspend fun runEntry(entry: Entry): EntryOutcome =
        try {
            entry.action()
            EntryOutcome.SUCCESS
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (@Suppress("SwallowedException") fk: SQLiteConstraintException) {
            EntryOutcome.FK_RETRY
        } catch (other: Exception) {
            onDrop(entry.key, other)
            EntryOutcome.PERMANENT_FAIL
        }

    private enum class EntryOutcome { SUCCESS, FK_RETRY, PERMANENT_FAIL }
}
