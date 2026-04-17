package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.port.repository.ExtractedUrlTrackerPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe tracker for URLs that have been extracted via the Tavily extract tool
 * during an active generation session.
 *
 * The tracker records URLs when extraction happens (before sources are persisted
 * to the database). After sources are persisted, the DAO's [TavilySourceDao.markExtracted]
 * is called for these URLs to set the extracted flag.
 *
 * This solves the timing issue where [TavilySourceDao.markExtracted] is called during tool
 * execution but sources haven't been inserted yet, so the UPDATE is a no-op.
 */
@Singleton
class ExtractedUrlTracker @Inject constructor() : ExtractedUrlTrackerPort {
    private val _urls = mutableSetOf<String>()

    override val urls: Set<String> get() = synchronized(_urls) { _urls.toSet() }

    override fun add(url: String) { synchronized(_urls) { _urls.add(url) } }

    override fun clear() = synchronized(_urls) { _urls.clear() }
}
