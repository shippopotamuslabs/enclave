package com.browntowndev.pocketcrew.domain.port.download

import java.io.File

interface HashingPort {
    fun calculateMd5(file: File): String
    fun calculateSha256(file: File): String
}