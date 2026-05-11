package com.browntowndev.pocketcrew.core.docgen

import com.browntowndev.pocketcrew.domain.port.docgen.DocumentValidationPort

class RustDocumentValidationAdapter : DocumentValidationPort {
    override fun validate(json: String): Result<Unit> {
        return try {
            validateDocument(json)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
