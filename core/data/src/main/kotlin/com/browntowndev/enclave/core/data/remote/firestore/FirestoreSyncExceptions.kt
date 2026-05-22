package com.browntowndev.enclave.core.data.remote.firestore

internal class FirestoreDocumentProcessingException(
    collectionPath: String,
    documentId: String,
    cause: Throwable,
) : Exception("Failed processing Firestore document $documentId in $collectionPath", cause)

internal class FirestoreSyncException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
