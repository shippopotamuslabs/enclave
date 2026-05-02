package com.browntowndev.pocketcrew.domain.port.media

/**
 * Port for sharing media assets from domain workflows.
 */
interface ShareMediaPort {
    fun shareMedia(uris: List<String>, mimeType: String)
}
