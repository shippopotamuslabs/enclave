package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.model.media.ShareDocumentBytesInput
import com.browntowndev.enclave.domain.port.media.MediaSaverPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.media.ShareMediaPort
import com.browntowndev.enclave.domain.port.repository.ModerationReportRepositoryPort
import com.browntowndev.enclave.domain.port.repository.UiSelectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Recording [ShareMediaPort] fake (ENC-28). */
class FakeShareMediaPort : ShareMediaPort {
    val shareMediaCalls = mutableListOf<Pair<List<String>, String>>()
    val shareDocumentCalls = mutableListOf<List<ShareDocumentBytesInput>>()

    override fun shareMedia(uris: List<String>, mimeType: String) {
        shareMediaCalls += uris to mimeType
    }

    override fun shareDocumentBytes(items: List<ShareDocumentBytesInput>) {
        shareDocumentCalls += items
    }
}

/** In-memory [UiSelectionRepository] fake (ENC-28). */
class FakeUiSelectionRepository(
    initialStudioMediaType: MediaCapability = MediaCapability.IMAGE,
) : UiSelectionRepository {
    private val chatModeState = MutableStateFlow(Mode.FAST)
    private val studioMediaTypeState = MutableStateFlow(initialStudioMediaType)

    override val selectedChatMode: Flow<Mode> = chatModeState
    override val selectedStudioMediaType: Flow<MediaCapability> = studioMediaTypeState

    val setStudioMediaTypeCalls = mutableListOf<MediaCapability>()

    /** Test driver: sets the persisted studio media type selection directly. */
    fun emitSelectedStudioMediaType(type: MediaCapability) {
        studioMediaTypeState.value = type
    }

    override suspend fun setChatMode(mode: Mode) {
        chatModeState.value = mode
    }

    override suspend fun setStudioMediaType(type: MediaCapability) {
        setStudioMediaTypeCalls += type
        studioMediaTypeState.value = type
    }
}

/** Recording [MediaSaverPort] fake (ENC-28). */
class FakeMediaSaverPort : MediaSaverPort {
    var saveResult: Result<String> = Result.success("content://saved")
    val saveCalls = mutableListOf<Pair<String, MediaCapability>>()

    override suspend fun saveToGallery(localUri: String, mediaType: MediaCapability): Result<String> {
        saveCalls += localUri to mediaType
        return saveResult
    }
}

/** Recording [ModerationReportRepositoryPort] fake (ENC-28). */
class FakeModerationReportRepository : ModerationReportRepositoryPort {
    val submittedReports = mutableListOf<Pair<String, String>>()
    var submitResult: Result<Unit> = Result.success(Unit)

    override suspend fun submitReport(prompt: String, outcome: String): Result<Unit> {
        submittedReports += prompt to outcome
        return submitResult
    }
}

/** Recording [TtsPlaybackControllerPort] fake (ENC-28). */
class FakeTtsPlaybackController : TtsPlaybackControllerPort {
    val playCalls = mutableListOf<String>()
    var stopCount = 0
    var playbackFlow: Flow<com.browntowndev.enclave.domain.port.media.TtsPlaybackStatus> =
        kotlinx.coroutines.flow.emptyFlow()

    override fun play(text: String): Flow<com.browntowndev.enclave.domain.port.media.TtsPlaybackStatus> {
        playCalls += text
        return playbackFlow
    }

    override fun stop() {
        stopCount++
    }

    override fun pause() = Unit

    override fun resume() = Unit
}
