package com.browntowndev.pocketcrew.app

import javax.inject.Qualifier

/**
 * Qualifier indicating that models are ready to use.
 * Used for dependency injection when models must be available.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ModelReady
