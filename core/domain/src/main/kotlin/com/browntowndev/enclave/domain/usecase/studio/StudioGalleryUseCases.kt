package com.browntowndev.enclave.domain.usecase.studio

import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.MediaProviderAsset
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.port.repository.StudioAlbumAsset
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.domain.port.media.ShareMediaPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.enclave.domain.port.repository.MediaProviderRepositoryPort
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.port.repository.UiSelectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveStudioAlbumsUseCase @Inject constructor(
    private val repository: StudioRepositoryPort,
) {
    operator fun invoke(): Flow<List<StudioAlbumAsset>> = repository.observeAllAlbums()
}

class ObserveStudioMediaUseCase @Inject constructor(
    private val repository: StudioRepositoryPort,
) {
    operator fun invoke(): Flow<List<StudioMediaAsset>> = repository.observeAllMedia()
}

/** Combined ephemeral + persisted studio media keyed by asset id (ENC-12). */
class ObserveChatStudioMediaUseCase @Inject constructor(
    private val repository: StudioRepositoryPort,
) {
    operator fun invoke(): Flow<Map<String, StudioMediaAsset>> =
        kotlinx.coroutines.flow.combine(
            repository.observeEphemeralMedia(),
            repository.observeAllMedia(),
        ) { ephemeral, persisted -> (ephemeral + persisted).associateBy { it.id } }
}

class CreateStudioAlbumUseCase @Inject constructor(
    private val repository: StudioRepositoryPort,
) {
    suspend operator fun invoke(name: String) = repository.createAlbum(name)
}

class RenameStudioAlbumUseCase @Inject constructor(
    private val repository: StudioRepositoryPort,
) {
    suspend operator fun invoke(albumId: String, name: String) = repository.renameAlbum(albumId, name)
}

class DeleteStudioMediaUseCase @Inject constructor(
    private val repository: StudioRepositoryPort,
) {
    suspend operator fun invoke(id: String) = repository.deleteMedia(id)
}

class DeleteStudioAlbumUseCase @Inject constructor(
    private val repository: StudioRepositoryPort,
) {
    suspend operator fun invoke(id: String) = repository.deleteAlbum(id)
}

class MoveStudioMediaToAlbumUseCase @Inject constructor(
    private val repository: StudioRepositoryPort,
) {
    suspend operator fun invoke(mediaIds: List<String>, targetAlbumId: String) =
        repository.moveMediaToAlbum(mediaIds, targetAlbumId)
}

class ShareStudioMediaUseCase @Inject constructor(
    private val shareMediaPort: ShareMediaPort,
) {
    operator fun invoke(uris: List<String>, mimeType: String) = shareMediaPort.shareMedia(uris, mimeType)
}

class ObserveDefaultModelAssignmentsUseCase @Inject constructor(
    private val repository: DefaultModelRepositoryPort,
) {
    operator fun invoke(): Flow<List<DefaultModelAssignment>> = repository.observeDefaults()
}

class GetMediaProvidersUseCase @Inject constructor(
    private val repository: MediaProviderRepositoryPort,
) {
    operator fun invoke(): Flow<List<MediaProviderAsset>> = repository.getMediaProviders()
}

class GetSelectedStudioMediaTypeUseCase @Inject constructor(
    private val repository: UiSelectionRepository,
) {
    operator fun invoke(): Flow<MediaCapability> = repository.selectedStudioMediaType
}

class SetSelectedStudioMediaTypeUseCase @Inject constructor(
    private val repository: UiSelectionRepository,
) {
    suspend operator fun invoke(type: MediaCapability) = repository.setStudioMediaType(type)
}
