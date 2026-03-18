package com.browntowndev.pocketcrew.domain.model.inference

import javax.inject.Qualifier

/**
 * Qualifier for the Fast model inference service.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.FIELD)
annotation class FastModelEngine

/**
 * Qualifier for the Thinking model inference service.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.FIELD)
annotation class ThinkingModelEngine
