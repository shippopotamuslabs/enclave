package com.browntowndev.pocketcrew.data.download

import com.browntowndev.pocketcrew.domain.port.HashingPort
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HashingService @Inject constructor() : HashingPort {

    override fun calculateMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        // Use java.util.Base64 (NO_WRAP equivalent)
        return Base64.getEncoder().encodeToString(digest.digest())
    }
}
