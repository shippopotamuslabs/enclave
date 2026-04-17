package com.browntowndev.pocketcrew.domain.port.repository

/**
 * Domain port for tracking URLs that have been extracted via the Tavily extract tool.
 * Used to re-apply extracted flags after sources are persisted to the database.
 */
interface ExtractedUrlTrackerPort {
    val urls: Set<String>
    fun add(url: String)
    fun clear()
}
