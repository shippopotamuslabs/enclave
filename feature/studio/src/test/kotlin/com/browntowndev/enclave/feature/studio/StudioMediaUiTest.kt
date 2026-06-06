package com.browntowndev.enclave.feature.studio

import com.browntowndev.enclave.domain.model.config.MediaCapability
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class StudioMediaUiTest {
    @Test
    fun effectiveLocalUri_returnsLocalUriWhenFileExists() {
        val file = File.createTempFile("studio", ".jpg")
        file.writeBytes(byteArrayOf(1, 2, 3))

        val asset =
            StudioMediaUi(
                id = "asset-1",
                localUri = "file://${file.absolutePath}",
                prompt = "Prompt",
                mediaType = MediaCapability.IMAGE,
                createdAt = 1L,
            )

        assertEquals("file://${file.absolutePath}", asset.effectiveLocalUri())
        file.delete()
    }

    @Test
    fun effectiveLocalUri_fallsBackToRemoteUrlWhenLocalFileMissing() {
        val asset =
            StudioMediaUi(
                id = "asset-1",
                localUri = "file:///tmp/does-not-exist.jpg",
                remoteUrl = "https://cdn.example.com/image.jpg",
                prompt = "Prompt",
                mediaType = MediaCapability.IMAGE,
                createdAt = 1L,
            )

        assertEquals("https://cdn.example.com/image.jpg", asset.effectiveLocalUri())
    }

    @Test
    fun effectiveLocalUri_returnsLocalUriWhenNoRemoteFallback() {
        val asset =
            StudioMediaUi(
                id = "asset-1",
                localUri = "file:///tmp/does-not-exist.jpg",
                prompt = "Prompt",
                mediaType = MediaCapability.IMAGE,
                createdAt = 1L,
            )

        assertEquals("file:///tmp/does-not-exist.jpg", asset.effectiveLocalUri())
    }

    @Test
    fun isAwaitingDownload_trueWhenRemoteOnlyAndNoLocalFile() {
        val asset =
            StudioMediaUi(
                id = "asset-1",
                localUri = "https://cdn.example.com/image.jpg",
                remoteUrl = "https://cdn.example.com/image.jpg",
                prompt = "Prompt",
                mediaType = MediaCapability.IMAGE,
                createdAt = 1L,
            )

        assertTrue(asset.isAwaitingDownload)
    }

    @Test
    fun isAwaitingDownload_falseWhenLocalFilePresent() {
        val file = File.createTempFile("studio", ".jpg")
        file.writeBytes(byteArrayOf(1, 2, 3))

        val asset =
            StudioMediaUi(
                id = "asset-1",
                localUri = "file://${file.absolutePath}",
                remoteUrl = "https://cdn.example.com/image.jpg",
                prompt = "Prompt",
                mediaType = MediaCapability.IMAGE,
                createdAt = 1L,
            )

        assertFalse(asset.isAwaitingDownload)
        file.delete()
    }

    @Test
    fun isAwaitingDownload_falseWhenNoRemoteUrl() {
        val asset =
            StudioMediaUi(
                id = "asset-1",
                localUri = "file:///tmp/does-not-exist.jpg",
                prompt = "Prompt",
                mediaType = MediaCapability.IMAGE,
                createdAt = 1L,
            )

        assertFalse(asset.isAwaitingDownload)
    }
}
