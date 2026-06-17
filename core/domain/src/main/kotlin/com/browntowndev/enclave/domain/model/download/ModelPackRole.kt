package com.browntowndev.enclave.domain.model.download

enum class ModelPackRole(
    val apiValue: String,
) {
    DIFFUSION("diffusion"),
    VAE("vae"),
    TEXT_ENCODER("text_encoder"),
    ;

    companion object {
        fun fromApiValue(value: String): ModelPackRole? = entries.firstOrNull { it.apiValue == value }
    }
}
