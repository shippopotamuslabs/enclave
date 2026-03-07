package com.browntowndev.pocketcrew.domain.port

import java.io.File

interface HashingPort {
    fun calculateMd5(file: File): String
}
