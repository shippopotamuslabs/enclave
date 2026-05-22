package com.browntowndev.enclave.core.docgen

import com.browntowndev.enclave.domain.port.docgen.DocumentValidationPort

class RustDocumentValidationAdapter : DocumentValidationPort {
    override fun validate(json: String): Result<Unit> =
        try {
            validateDocument(json)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    override fun validateStartArtifactArgs(json: String): Result<Unit> =
        try {
            validateStartArtifactParams(json)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }

    override fun validateAppendBlocksArgs(json: String): Result<Unit> =
        try {
            validateAppendBlocksParams(json)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
}
