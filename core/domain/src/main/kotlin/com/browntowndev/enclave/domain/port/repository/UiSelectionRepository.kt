package com.browntowndev.enclave.domain.port.repository

import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.model.config.MediaCapability
import kotlinx.coroutines.flow.Flow

/**
 * Persists the user's last-selected screen-level choices so they can be restored on the next
 * session. Backed by DataStore (not Room); see `UiSelectionRepositoryImpl`.
 */
interface UiSelectionRepository {
    /** The last chat mode the user selected in the Chat top bar. */
    val selectedChatMode: Flow<Mode>

    /** The last media type the user selected in the Studio top bar. */
    val selectedStudioMediaType: Flow<MediaCapability>

    suspend fun setChatMode(mode: Mode)

    suspend fun setStudioMediaType(type: MediaCapability)
}
