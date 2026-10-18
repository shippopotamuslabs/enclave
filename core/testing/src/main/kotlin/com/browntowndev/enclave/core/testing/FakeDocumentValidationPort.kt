package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.port.docgen.DocumentValidationPort

/**
 * [DocumentValidationPort] fake (ENC-28): every validation succeeds unless a
 * test programs a failure result for the corresponding entry point.
 */
class FakeDocumentValidationPort : DocumentValidationPort {
    var validateResult: Result<Unit> = Result.success(Unit)
    var validateStartArtifactArgsResult: Result<Unit> = Result.success(Unit)
    var validateAppendBlocksArgsResult: Result<Unit> = Result.success(Unit)

    val validateCalls = mutableListOf<String>()
    val validateStartArtifactArgsCalls = mutableListOf<String>()
    val validateAppendBlocksArgsCalls = mutableListOf<String>()

    override fun validate(json: String): Result<Unit> {
        validateCalls += json
        return validateResult
    }

    override fun validateStartArtifactArgs(json: String): Result<Unit> {
        validateStartArtifactArgsCalls += json
        return validateStartArtifactArgsResult
    }

    override fun validateAppendBlocksArgs(json: String): Result<Unit> {
        validateAppendBlocksArgsCalls += json
        return validateAppendBlocksArgsResult
    }
}
