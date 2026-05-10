package com.browntowndev.pocketcrew.domain.port.docgen

/**
 * Port for validating documents using the Rust library.
 */
interface DocumentValidationPort {
    fun validate(json: String): Result<Unit>
}
