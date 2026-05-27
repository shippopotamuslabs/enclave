package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import com.browntowndev.enclave.domain.usecase.security.AdoptCloudVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.BootstrapCloudVaultFromLocalUseCase
import com.browntowndev.enclave.domain.usecase.security.CreateVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.DetectVaultReconciliationStateUseCase
import com.browntowndev.enclave.domain.usecase.security.StartCloudSyncListeningUseCase
import com.browntowndev.enclave.domain.usecase.security.UnlockVaultFromStoredMetadataUseCase
import com.browntowndev.enclave.domain.usecase.security.VaultReconciliationOutcome
import com.browntowndev.enclave.domain.usecase.settings.SettingsAuthUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsPreferencesUseCases
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsGeneralDelegateTest {
    private val preferencesUseCases = mockk<SettingsPreferencesUseCases>(relaxed = true)
    private val authUseCases = mockk<SettingsAuthUseCases>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>()
    private val detectVaultReconciliationStateUseCase = mockk<DetectVaultReconciliationStateUseCase>()
    private val adoptCloudVaultUseCase = mockk<AdoptCloudVaultUseCase>(relaxed = true)
    private val bootstrapCloudVaultFromLocalUseCase = mockk<BootstrapCloudVaultFromLocalUseCase>(relaxed = true)
    private val createVaultUseCase = mockk<CreateVaultUseCase>()
    private val unlockVaultFromStoredMetadataUseCase = mockk<UnlockVaultFromStoredMetadataUseCase>()
    private val startCloudSyncListeningUseCase = mockk<StartCloudSyncListeningUseCase>()
    private val cryptoManager = mockk<CryptoManagerPort>()
    private val vaultMetadataPort = mockk<VaultMetadataPort>()
    private val cloudSyncPort = mockk<CloudSyncPort>()

    private lateinit var delegate: SettingsGeneralDelegate

    @BeforeEach
    fun setup() {
        every {
            errorHandler.coroutineExceptionHandler(any(), any(), any())
        } returns CoroutineExceptionHandler { _, _ -> }

        delegate =
            SettingsGeneralDelegate(
                preferencesUseCases = preferencesUseCases,
                authUseCases = authUseCases,
                errorHandler = errorHandler,
                detectVaultReconciliationStateUseCase = detectVaultReconciliationStateUseCase,
                adoptCloudVaultUseCase = adoptCloudVaultUseCase,
                bootstrapCloudVaultFromLocalUseCase = bootstrapCloudVaultFromLocalUseCase,
                createVaultUseCase = createVaultUseCase,
                unlockVaultFromStoredMetadataUseCase = unlockVaultFromStoredMetadataUseCase,
                startCloudSyncListeningUseCase = startCloudSyncListeningUseCase,
                cryptoManager = cryptoManager,
                vaultMetadataPort = vaultMetadataPort,
                cloudSyncPort = cloudSyncPort,
            )
    }

    @Test
    fun `google sign-in clears cached dek and stops cloud listeners before re-auth sheet`() =
        runTest {
            val sheet = MutableStateFlow<VaultReauthSheetState>(VaultReauthSheetState.Hidden)
            coEvery { authUseCases.loginWithGoogle() } returns Result.success(mockk(relaxed = true))
            every { cryptoManager.clearKey() } returns Unit
            every { cloudSyncPort.stopListening() } returns Unit

            delegate.onLoginWithGoogle(this, "Settings", sheet)
            advanceUntilIdle()

            assertEquals(VaultReauthSheetState.EnterPassword(), sheet.value)
            verify(exactly = 1) { cryptoManager.clearKey() }
            verify(exactly = 1) { cloudSyncPort.stopListening() }
            verify(exactly = 0) { cloudSyncPort.startListening() }
        }

    @Test
    fun `no cloud vault with local material bootstraps then unlocks and syncs`() =
        runTest {
            val sheet = MutableStateFlow<VaultReauthSheetState>(VaultReauthSheetState.Hidden)
            coEvery { detectVaultReconciliationStateUseCase("password") } returns
                Result.success(VaultReconciliationOutcome.NoCloudVault)
            coEvery { vaultMetadataPort.getVaultSalt() } returns "salt-b64"
            coEvery { vaultMetadataPort.getWrappedDek() } returns "wrapped-b64"
            coEvery { bootstrapCloudVaultFromLocalUseCase() } returns Result.success(Unit)
            coEvery { unlockVaultFromStoredMetadataUseCase("password") } returns Result.success(Unit)
            coEvery { startCloudSyncListeningUseCase(uploadLocalFirst = true) } returns Result.success(Unit)

            delegate.onSubmitVaultReauthPassword(this, "Settings", sheet, "password")
            advanceUntilIdle()

            assertEquals(VaultReauthSheetState.Hidden, sheet.value)
            coVerify(exactly = 1) { bootstrapCloudVaultFromLocalUseCase() }
            coVerify(exactly = 1) { unlockVaultFromStoredMetadataUseCase("password") }
            coVerify(exactly = 1) { startCloudSyncListeningUseCase(uploadLocalFirst = true) }
            coVerify(exactly = 0) { createVaultUseCase(any(), any()) }
        }

    @Test
    fun `no cloud vault and no local vault creates the first vault`() =
        runTest {
            val sheet = MutableStateFlow<VaultReauthSheetState>(VaultReauthSheetState.Hidden)
            val salt = ByteArray(16) { 0x11 }
            val wrappedDek = ByteArray(32) { 0x22 }
            val verificationCiphertext = ByteArray(8) { 0x33 }

            coEvery { detectVaultReconciliationStateUseCase("password") } returns
                Result.success(VaultReconciliationOutcome.NoCloudVault)
            coEvery { vaultMetadataPort.getVaultSalt() } returns null
            coEvery { vaultMetadataPort.getWrappedDek() } returns null
            coEvery { vaultMetadataPort.getVaultVerificationData() } returns null
            every { cryptoManager.generateSalt() } returns salt
            every { cryptoManager.encodeBase64(salt) } returns "salt-b64"
            every { cryptoManager.encodeBase64(wrappedDek) } returns "wrapped-b64"
            every { cryptoManager.encodeBase64(verificationCiphertext) } returns "verification-b64"
            every { cryptoManager.encrypt(any()) } returns Result.success(verificationCiphertext)
            coEvery { createVaultUseCase("password", salt) } returns Result.success(wrappedDek)
            coEvery { vaultMetadataPort.saveVaultSalt("salt-b64") } returns Unit
            coEvery { vaultMetadataPort.saveWrappedDek("wrapped-b64") } returns Unit
            coEvery { vaultMetadataPort.saveVaultVerificationData("verification-b64") } returns Unit
            coEvery { cloudSyncPort.uploadVaultMetadata(any()) } returns Result.success(Unit)
            coEvery { startCloudSyncListeningUseCase(uploadLocalFirst = false) } returns Result.success(Unit)

            delegate.onSubmitVaultReauthPassword(this, "Settings", sheet, "password")
            advanceUntilIdle()

            assertEquals(VaultReauthSheetState.Hidden, sheet.value)
            coVerify(exactly = 1) { createVaultUseCase("password", salt) }
            coVerify(exactly = 1) { vaultMetadataPort.saveVaultSalt("salt-b64") }
            coVerify(exactly = 1) { vaultMetadataPort.saveWrappedDek("wrapped-b64") }
            coVerify(exactly = 1) { vaultMetadataPort.saveVaultVerificationData("verification-b64") }
            coVerify(exactly = 1) { cloudSyncPort.uploadVaultMetadata(any()) }
            coVerify(exactly = 1) { startCloudSyncListeningUseCase(uploadLocalFirst = false) }
            verify(exactly = 0) { cloudSyncPort.startListening() }
            coVerify(exactly = 0) { bootstrapCloudVaultFromLocalUseCase() }
        }

    @Test
    fun `already matching unlocks starts cloud sync and hides sheet`() =
        runTest {
            val sheet = MutableStateFlow<VaultReauthSheetState>(VaultReauthSheetState.Hidden)
            coEvery { detectVaultReconciliationStateUseCase("password") } returns
                Result.success(VaultReconciliationOutcome.AlreadyMatching)
            coEvery { unlockVaultFromStoredMetadataUseCase("password") } returns Result.success(Unit)
            coEvery { startCloudSyncListeningUseCase(uploadLocalFirst = true) } returns Result.success(Unit)

            delegate.onSubmitVaultReauthPassword(this, "Settings", sheet, "password")
            advanceUntilIdle()

            assertEquals(VaultReauthSheetState.Hidden, sheet.value)
            coVerifyOrder {
                detectVaultReconciliationStateUseCase("password")
                unlockVaultFromStoredMetadataUseCase("password")
                startCloudSyncListeningUseCase(uploadLocalFirst = true)
            }
            coVerify(exactly = 0) { bootstrapCloudVaultFromLocalUseCase() }
            coVerify(exactly = 0) { createVaultUseCase(any(), any()) }
        }

    @Test
    fun `password mismatch shows inline error and keeps sheet open`() =
        runTest {
            val sheet = MutableStateFlow<VaultReauthSheetState>(VaultReauthSheetState.Hidden)
            coEvery { detectVaultReconciliationStateUseCase("wrong") } returns
                Result.success(VaultReconciliationOutcome.PasswordMismatch)

            delegate.onSubmitVaultReauthPassword(this, "Settings", sheet, "wrong")
            advanceUntilIdle()

            assertEquals(
                VaultReauthSheetState.EnterPassword(
                    isUnlocking = false,
                    errorMessage = "Incorrect Vault Password",
                ),
                sheet.value,
            )
            coVerify(exactly = 0) { unlockVaultFromStoredMetadataUseCase(any()) }
            coVerify(exactly = 0) { startCloudSyncListeningUseCase(any()) }
        }

    @Test
    fun `sync start failure keeps reauth sheet open with error`() =
        runTest {
            val sheet = MutableStateFlow<VaultReauthSheetState>(VaultReauthSheetState.Hidden)
            coEvery { detectVaultReconciliationStateUseCase("password") } returns
                Result.success(VaultReconciliationOutcome.AlreadyMatching)
            coEvery { unlockVaultFromStoredMetadataUseCase("password") } returns Result.success(Unit)
            coEvery { startCloudSyncListeningUseCase(uploadLocalFirst = true) } returns
                Result.failure(IllegalStateException("replay failed"))

            delegate.onSubmitVaultReauthPassword(this, "Settings", sheet, "password")
            advanceUntilIdle()

            assertEquals(
                VaultReauthSheetState.EnterPassword(
                    isUnlocking = false,
                    errorMessage = "Failed to start cloud sync",
                ),
                sheet.value,
            )
            coVerify(exactly = 1) { startCloudSyncListeningUseCase(uploadLocalFirst = true) }
        }

    @Test
    fun `SCN-ENTRY-003 settings reauth and adoption start ordered sync`() =
        runTest {
            val sheet = MutableStateFlow<VaultReauthSheetState>(VaultReauthSheetState.Hidden)
            val salt = ByteArray(16) { 0x11 }
            val wrappedDek = ByteArray(32) { 0x22 }
            val payload =
                VaultReconciliationOutcome.MismatchRequiresClobber(
                    cloudDek = ByteArray(32) { 0x33 },
                    cloudSalt = "cloud-salt",
                    cloudWrappedDek = "cloud-wrapped",
                    cloudVerification = null,
                )

            // Drive the entry path through a REAL use case so the test fails when the use case
            // does not actually replay before starting the listener.
            coEvery { cloudSyncPort.syncAllLocalDataToCloud() } returns Result.success(Unit)
            coEvery { cloudSyncPort.syncRemoteChangesFromCloud() } returns
                Result.success(
                    com.browntowndev.enclave.domain.model.sync.CloudSyncEventReplayResult(
                        eventsApplied = 0,
                        lastAppliedSequence = 0,
                        hasMore = false,
                    ),
                )
            every { cloudSyncPort.startListening() } returns Unit
            val realStartUseCase =
                com.browntowndev.enclave.domain.usecase.security
                    .StartCloudSyncListeningUseCase(cloudSyncPort)
            val realDelegate =
                SettingsGeneralDelegate(
                    preferencesUseCases = preferencesUseCases,
                    authUseCases = authUseCases,
                    errorHandler = errorHandler,
                    detectVaultReconciliationStateUseCase = detectVaultReconciliationStateUseCase,
                    adoptCloudVaultUseCase = adoptCloudVaultUseCase,
                    bootstrapCloudVaultFromLocalUseCase = bootstrapCloudVaultFromLocalUseCase,
                    createVaultUseCase = createVaultUseCase,
                    unlockVaultFromStoredMetadataUseCase = unlockVaultFromStoredMetadataUseCase,
                    startCloudSyncListeningUseCase = realStartUseCase,
                    cryptoManager = cryptoManager,
                    vaultMetadataPort = vaultMetadataPort,
                    cloudSyncPort = cloudSyncPort,
                )

            coEvery { detectVaultReconciliationStateUseCase("password") } returns
                Result.success(VaultReconciliationOutcome.AlreadyMatching)
            coEvery { vaultMetadataPort.getVaultSalt() } returns "salt-b64"
            coEvery { vaultMetadataPort.getWrappedDek() } returns "wrapped-b64"
            every { cryptoManager.decodeBase64("salt-b64") } returns salt
            every { cryptoManager.decodeBase64("wrapped-b64") } returns wrappedDek
            coEvery { unlockVaultFromStoredMetadataUseCase("password") } returns Result.success(Unit)

            realDelegate.onSubmitVaultReauthPassword(this, "Settings", sheet, "password")
            advanceUntilIdle()

            assertEquals(VaultReauthSheetState.Hidden, sheet.value)
            coVerify(exactly = 1) { unlockVaultFromStoredMetadataUseCase("password") }
            // Reauth path must drive upload -> replay -> live listener in order.
            coVerifyOrder {
                cloudSyncPort.syncAllLocalDataToCloud()
                cloudSyncPort.syncRemoteChangesFromCloud()
                cloudSyncPort.startListening()
            }

            coEvery { detectVaultReconciliationStateUseCase("password") } returns Result.success(payload)
            coEvery { adoptCloudVaultUseCase(payload) } returns Result.success(Unit)

            realDelegate.onSubmitVaultReauthPassword(this, "Settings", sheet, "password")
            advanceUntilIdle()
            realDelegate.onConfirmAdoptCloud(this, "Settings", sheet)
            advanceUntilIdle()

            // Adoption path must skip upload but still drive replay -> live listener.
            coVerify(atLeast = 1) { cloudSyncPort.syncRemoteChangesFromCloud() }
            verify(atLeast = 1) { cloudSyncPort.startListening() }
        }
}
