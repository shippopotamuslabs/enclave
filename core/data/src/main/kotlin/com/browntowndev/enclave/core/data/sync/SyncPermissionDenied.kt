package com.browntowndev.enclave.core.data.sync

import androidx.work.ListenableWorker
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.StorageException

/**
 * True when any error in the causal chain is a server-side subscription denial:
 * Firestore / Functions `PERMISSION_DENIED`, or Storage `not-authorized`
 * ([StorageException.ERROR_NOT_AUTHORIZED]). Callers use this to stop cleanly
 * ([androidx.work.ListenableWorker.Result.failure]) instead of retrying forever
 * against a gate that will never open.
 */
internal fun Throwable?.isPermissionDenied(): Boolean {
    var current = this
    while (current != null) {
        when (current) {
            is FirebaseFirestoreException ->
                if (current.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) return true
            is FirebaseFunctionsException ->
                if (current.code == FirebaseFunctionsException.Code.PERMISSION_DENIED) return true
            is StorageException ->
                if (current.errorCode == StorageException.ERROR_NOT_AUTHORIZED) return true
        }
        current = current.cause
    }
    return false
}

/**
 * Maps collected worker failures to a [ListenableWorker.Result]: no failures
 * succeeds, any subscription denial stops terminally instead of retrying
 * forever, and anything else keeps the existing retry behavior.
 */
internal fun List<Throwable>.toWorkerResult(): ListenableWorker.Result =
    when {
        isEmpty() -> ListenableWorker.Result.success()
        any { it.isPermissionDenied() } -> ListenableWorker.Result.failure()
        else -> ListenableWorker.Result.retry()
    }
