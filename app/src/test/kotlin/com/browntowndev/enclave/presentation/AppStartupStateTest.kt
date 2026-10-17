package com.browntowndev.enclave.presentation

import com.browntowndev.enclave.core.ui.component.vault.VaultAuthUiState
import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.model.download.DownloadModelsResult
import com.browntowndev.enclave.domain.model.download.ModelScanResult
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.presentation.navigation.Routes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppStartupStateTest {
    @Test
    fun `Ready state should have isUnlocking property defaulting to false`() {
        val readyState =
            AppStartupState.Ready(
                authState = VaultAuthUiState.Unauthenticated,
                modelsResult = null,
            )

        assertFalse(readyState.isUnlocking)
    }

    @Test
    fun `Ready state isUnlocking property can be set to true`() {
        val readyState =
            AppStartupState.Ready(
                authState = VaultAuthUiState.Unauthenticated,
                modelsResult = null,
                isUnlocking = true,
            )

        assertTrue(readyState.isUnlocking)
    }

    @Test
    fun `Ready state isUnlocking property survives copy operation`() {
        val readyState =
            AppStartupState.Ready(
                authState = VaultAuthUiState.Unauthenticated,
                modelsResult = null,
                isUnlocking = true,
            )

        val copiedState = readyState.copy(vaultErrorMessage = "some error")

        assertTrue(copiedState.isUnlocking)
    }

    @Test
    fun `initialRoute should be MODEL_DOWNLOAD when initializationError is not null`() {
        val readyState =
            AppStartupState.Ready(
                authState = VaultAuthUiState.Unauthenticated,
                modelsResult = null,
                initializationError = "Error",
            )

        assertEquals(Routes.MODEL_DOWNLOAD, readyState.initialRoute)
    }

    @Test
    fun `initialRoute should be MODEL_DOWNLOAD when modelsResult has modelsToDownload`() {
        val modelsResult = modelsResult(modelsToDownload = listOf(sampleAsset()))

        val readyState =
            AppStartupState.Ready(
                authState = VaultAuthUiState.Unauthenticated,
                modelsResult = modelsResult,
            )

        assertEquals(Routes.MODEL_DOWNLOAD, readyState.initialRoute)
    }

    @Test
    fun `initialRoute should be MODEL_DOWNLOAD when modelsResult is null and scan is pending`() {
        val readyState =
            AppStartupState.Ready(
                authState = VaultAuthUiState.Unauthenticated,
                modelsResult = null,
            )

        assertEquals(Routes.MODEL_DOWNLOAD, readyState.initialRoute)
    }

    @Test
    fun `initialRoute should be CHAT when no initializationError and modelsToDownload is empty`() {
        val modelsResult = modelsResult(modelsToDownload = emptyList())

        val readyState =
            AppStartupState.Ready(
                authState = VaultAuthUiState.Unauthenticated,
                modelsResult = modelsResult,
            )

        assertEquals(Routes.CHAT, readyState.initialRoute)
    }

    private fun modelsResult(modelsToDownload: List<LocalModelAsset>): DownloadModelsResult =
        DownloadModelsResult(
            allModels = emptyMap(),
            modelsToDownload = modelsToDownload,
            scanResult =
                ModelScanResult(
                    missingModels = emptyList(),
                    partialDownloads = emptyMap(),
                    allValid = true,
                ),
        )

    private fun sampleAsset(): LocalModelAsset =
        LocalModelAsset(
            metadata =
                LocalModelMetadata(
                    id = LocalModelId("test-model"),
                    huggingFaceModelName = "hf/test-model",
                    remoteFileName = "model.litertlm",
                    localFileName = "model.litertlm",
                    sha256 = "abc123",
                    sizeInBytes = 1,
                    modelFileFormat = ModelFileFormat.LITERTLM,
                ),
            configurations = emptyList(),
        )
}
