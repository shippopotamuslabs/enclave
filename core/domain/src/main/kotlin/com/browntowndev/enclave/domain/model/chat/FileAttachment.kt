package com.browntowndev.enclave.domain.model.chat

/**
 * Domain model for file attachments.
 *
 * @property name The display name of the file
 * @property content The raw text content of the file
 */
data class FileAttachment(
    val name: String,
    val content: String,
)
