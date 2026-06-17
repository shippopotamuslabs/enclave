package com.browntowndev.enclave.feature.inference.media

import javax.inject.Qualifier

/**
 * Qualifies the real, non-decorated [com.browntowndev.enclave.domain.port.media.ImageGenerationPort]
 * implementation so the moderation decorator can wrap it without creating a DI cycle.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
annotation class DelegateImageGenerationPort
