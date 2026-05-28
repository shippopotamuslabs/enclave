package com.browntowndev.enclave.core.data.remote.firestore

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await

internal class FirestoreSyncDocumentCache(
    private val firestore: FirebaseFirestore,
    private val maxConcurrency: Int = 4,
) : FirestoreSyncSourceDocumentStore {
    private val cache = mutableMapOf<String, DocumentSnapshot>()
    private val semaphore = Semaphore(maxConcurrency)

    suspend fun prefetch(
        userId: String,
        events: List<FirestoreSyncEvent>,
    ) {
        val upsertEvents = events.filter { it.operation != "delete" }
        if (upsertEvents.isEmpty()) return

        val fetchedPaths = mutableSetOf<String>()
        synchronized(cache) {
            fetchedPaths.addAll(cache.keys)
        }

        // Initialize root paths
        val rootPaths = upsertEvents.map { "users/$userId/${it.collectionPath}/${it.entityId}" }
        val uniqueRootPaths = rootPaths.filter { it !in fetchedPaths }.toSet()
        if (uniqueRootPaths.isEmpty()) return

        // Fetch roots in parallel
        val rootSnapshots = fetchDocumentsParallel(uniqueRootPaths)
        synchronized(cache) {
            rootSnapshots.forEach { (path, snapshot) ->
                if (snapshot != null) {
                    cache[path] = snapshot
                    fetchedPaths.add(path)
                }
            }
        }

        // Recursively resolve and fetch parents
        var currentSnapshots = rootSnapshots.values.filterNotNull()
        while (currentSnapshots.isNotEmpty()) {
            val parentRefs = mutableSetOf<String>()
            for (snapshot in currentSnapshots) {
                val pathSegments = snapshot.reference.path.split("/")
                if (pathSegments.size >= 4) {
                    val collectionPath = pathSegments[2]
                    val data = snapshot.data ?: emptyMap()
                    val parents = FirestoreSyncDependencyGraph.requiredParents(collectionPath, data)
                    for (parent in parents) {
                        val parentPath = "users/$userId/${parent.collectionPath}/${parent.entityId}"
                        if (parentPath !in fetchedPaths) {
                            parentRefs.add(parentPath)
                        }
                    }
                }
            }

            if (parentRefs.isEmpty()) break

            val parentSnapshots = fetchDocumentsParallel(parentRefs)
            synchronized(cache) {
                parentSnapshots.forEach { (path, snapshot) ->
                    if (snapshot != null) {
                        cache[path] = snapshot
                        fetchedPaths.add(path)
                    }
                }
            }
            currentSnapshots = parentSnapshots.values.filterNotNull()
        }
    }

    private suspend fun fetchDocumentsParallel(paths: Set<String>): Map<String, DocumentSnapshot?> =
        coroutineScope {
            paths
                .map { path ->
                    async {
                        path to fetchDocumentWithSemaphore(path)
                    }
                }.awaitAll()
                .toMap()
        }

    @Suppress("SwallowedException")
    private suspend fun fetchDocumentWithSemaphore(path: String): DocumentSnapshot? =
        semaphore.withPermit {
            try {
                val snapshot = firestore.document(path).get().await()
                if (snapshot.exists()) snapshot else null
            } catch (e: Exception) {
                null
            }
        }

    fun getCachedSnapshot(path: String): DocumentSnapshot? =
        synchronized(cache) {
            cache[path]
        }

    fun invalidate(path: String) {
        synchronized(cache) {
            cache.remove(path)
        }
    }

    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
    }

    override suspend fun getDocument(path: String): Map<String, Any?>? {
        val cached = getCachedSnapshot(path)
        if (cached != null) {
            return cached.data
        }
        val snapshot = fetchDocumentWithSemaphore(path)
        if (snapshot != null) {
            synchronized(cache) {
                cache[path] = snapshot
            }
            return snapshot.data
        }
        return null
    }
}
