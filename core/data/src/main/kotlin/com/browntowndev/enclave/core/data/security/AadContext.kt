package com.browntowndev.enclave.core.data.security

data class AadContext(
    val owner: String,
    val field: String,
    val entityId: String,
) {
    fun toBytes(): ByteArray = "$owner|$field|$entityId".toByteArray(Charsets.UTF_8)
}
