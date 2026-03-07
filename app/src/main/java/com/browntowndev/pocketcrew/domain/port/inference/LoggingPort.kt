package com.browntowndev.pocketcrew.domain.port.inference

/**
 * Domain port for logging operations.
 * Implementations can use Android Log, console, file logging, or any other logging mechanism.
 * This abstraction allows domain code to log without depending on Android SDK.
 */
interface LoggingPort {
    fun debug(tag: String, message: String)

    fun info(tag: String, message: String)

    fun warning(tag: String, message: String)

    fun error(tag: String, message: String, throwable: Throwable? = null)
}