package com.browntowndev.pocketcrew.core.data.remote

import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.google.firebase.firestore.Blob
import java.security.MessageDigest

internal fun String.encrypt(cryptoManager: CryptoManagerPort): Blob = 
    Blob.fromBytes(cryptoManager.encrypt(this).getOrThrow())

internal fun Blob.decrypt(cryptoManager: CryptoManagerPort): String? {
    val bytes = this.toBytes()
    return if (bytes != null && bytes.isNotEmpty()) {
        cryptoManager.decrypt(bytes).getOrNull()
    } else {
        null
    }
}

internal fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

