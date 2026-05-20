package com.browntowndev.pocketcrew.domain.port.download

interface HashingPort {
    fun calculateMd5(filePath: String): String

    fun calculateSha256(filePath: String): String
}
