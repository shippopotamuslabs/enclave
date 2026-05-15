package com.browntowndev.pocketcrew.domain.port.docgen

/**
 * Port for validating documents and LLM tool arguments using the Rust docgen library.
 */
interface DocumentValidationPort {
    fun validate(json: String): Result<Unit>

    /** Validates JSON for the `start_artifact` tool (Rust [StartArtifactParams]). */
    fun validateStartArtifactArgs(json: String): Result<Unit>

    /** Validates JSON for the `append_blocks` tool (Rust [AppendBlocksParams]). */
    fun validateAppendBlocksArgs(json: String): Result<Unit>
}
