package com.browntowndev.enclave.domain.port.media

import com.browntowndev.enclave.domain.model.media.ShareDocumentBytesInput

/**
 * Port for sharing media assets from domain workflows.
 */
interface ShareMediaPort {
    fun shareMedia(
        uris: List<String>,
        mimeType: String,
    )

    /**
     * Shares one or more generated documents. Implementations must stage [ShareDocumentBytesInput.bytes]
     * to accessible URIs (e.g. cache + FileProvider) and preserve each item's MIME type for the system share sheet.
     */
    fun shareDocumentBytes(items: List<ShareDocumentBytesInput>)
}
