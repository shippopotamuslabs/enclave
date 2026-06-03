package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.port.repository.ModerationReportRepositoryPort
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModerationReportRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ModerationReportRepositoryPort {

    override suspend fun submitReport(prompt: String, outcome: String): Result<Unit> = runCatching {
        val report = mapOf(
            "prompt" to prompt,
            "outcome" to outcome,
            "timestamp" to FieldValue.serverTimestamp(),
            "version" to 1
        )
        firestore.collection("moderation").add(report).await()
        Unit
    }
}
