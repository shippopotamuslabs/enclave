package com.browntowndev.pocketcrew.domain.qualifier

import javax.inject.Qualifier

/**
 * Qualifier for the application-level CoroutineScope.
 * Components injected with this scope should live for the lifetime of the application.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.FIELD
)
annotation class ApplicationScope
