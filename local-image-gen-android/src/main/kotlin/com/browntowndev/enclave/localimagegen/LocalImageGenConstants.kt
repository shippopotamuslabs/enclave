package com.browntowndev.enclave.localimagegen

/** Public JNI mode and memory-policy tokens for callers outside this module. */
object LocalImageGenConstants {
    const val MODE_TXT2IMG = 0
    const val MODE_IMG2IMG = 1

    const val MEMORY_POLICY_FULL = 0
    const val MEMORY_POLICY_LOW_RAM = 1
}
