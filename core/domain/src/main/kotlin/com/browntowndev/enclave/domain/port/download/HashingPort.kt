package com.browntowndev.enclave.domain.port.download

interface HashingPort {
    fun calculateMd5(filePath: String): String

    fun calculateSha256(filePath: String): String
}
