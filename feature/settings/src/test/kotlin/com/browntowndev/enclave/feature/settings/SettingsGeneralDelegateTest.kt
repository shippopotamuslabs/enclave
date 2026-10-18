package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.core.testing.FakeCryptoManagerPort
import com.browntowndev.enclave.core.testing.FakeVaultMetadataPort
import com.browntowndev.enclave.domain.port.auth.UserProfile
import com.browntowndev.enclave.domain.usecase.security.VaultReconciliationOutcome
import org.junit.jupiter.api.Assertions.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SettingsGeneralDelegateTest {
    private val preferencesUseCases = FakeSettingsPreferencesUseCases()
    private val authUseCases = FakeSettingsAuthUseCases()
    private val errorHandler = FakeViewModelErrorHandler()
    private val vaultFlowEvents = mutableListOf<String>()
    private val detectVaultReconciliationStateUseCase = FakeDetectVaultReconciliationStateUseCase(vaultFlowEvents)
    private val adoptCloudVaultUseCase = FakeAdoptCloudVaultUseCase()
    private val bootstrapCloudVaultFromLocalUseCase = FakeBootstrapCloudVaultFromLocalUseCase()
    private val createVaultUseCase = FakeCreateVaultUseCase()
    private val unlockVaultFromStoredMetadataUseCase = FakeUnlockVaultFromStoredMetadataUseCase(vaultFlowEvents)
    private val startCloudSyncListeningUseCase = FakeStartCloudSyncListeningUseCase(vaultFlowEvents)
    private val cryptoManager = FakeCryptoManagerPort()
    private val vaultMetadataPort = FakeVaultMetadataPort()
    private val cloudSyncPort = FakeCloudSyncPort()

    private lateinit var delegate: SettingsGeneralDelegate

    @BeforeEach
    fun setup() {
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
            authUseCases.loginWithGoogle.result =
                Result.success(UserProfile(uid = "uid-1", displayName = null, email = null, photoUrl = null))

            delegate.onLoginWithGoogle(this, "Settings", sheet)
            advanceUntilIdle()

            assertEquals(VaultReauthSheetState.EnterPassword(), sheet.value)
            assertEquals(1, cryptoManager.clearKeyCallCount)
            assertEquals(1, cloudSyncPort.count("stopListening"))
            assertEquals(0, cloudSyncPort.count("startListening"))
        }

    @Test
    fun `no cloud vault with local material bootstraps then unlocks and syncs`() =
        runTest {
            val sheet = MutableStateFlow<VaultReauthSheetState>(VaultReauthSheetState.Hidden)
            detectVaultReconciliationStateUseCase.result =
                Result.success(VaultReconciliationOutcome.NoCloudVault)
            vaultMetadataPort.salt = "salt-b64"
            vaultMetadataPort.wrappedDek = "wrapped-b64"

            delegate.onSubmitVaultReauthPassword(this, "Settings", sheet, "password")
            advanceUntilIdle()

            assertEquals(VaultReauthSheetState.Hidden, sheet.value)
            assertEquals(1, bootstrapCloudVaultFromLocalUseCase.callCount)
            assertEquals(listOf("password"), unlockVaultFromStoredMetadataUseCase.calls)
            assertEquals(listOf(true), startCloudSyncListeningUseCase.calls)
            assertTrue(createVaultUseCase.calls.isEmpty())
        }

    @Test
    fun `no cloud vault and no local vault creates the first vault`() =
        runTest {
            val sheet = MutableStateFlow<VaultReauthSheetState>(VaultReauthSheetState.Hidden)
            val salt = ByteArray(16) { 0x11 }
            val wrappedDek = ByteArray(32) { 0x22 }
            val verificationCiphertext = ByteArray(8) { 0x33 }

            detectVaultReconciliationStateUseCase.result =
                Result.success(VaultReconciliationOutcome.NoCloudVault)
            cryptoManager.generatedSalt = salt
            cryptoManager.encryptResult = Result.success(verificationCiphertext)
            createVaultUseCase.result = Result.success(wrappedDek)
            startCloudSyncListeningUseCase.result = Result.success(Unit)

            delegate.onSubmitVaultReauthPassword(this, "Settings", sheet, "password")
            advanceUntilIdle()

            assertEquals(VaultReauthSheetState.Hidden, sheet.value)
            assertEquals(1, createVaultUseCase.calls.size)
            assertEquals("password", createVaultUseCase.calls.single().first)
            assertTrue(salt.contentEquals(createVaultUseCase.calls.single().second))
            assertEquals(listOf(cryptoManager.encodeBase64(salt)), vaultMetadataPort.savedSalts)
            assertEquals(listOf(cryptoManager.encodeBase64(wrappedDek)), vaultMetadataPort.savedWrappedDeks)
            assertEquals(
                listOf(cryptoManager.encodeBase64(verificationCiphertext)),
                vaultMetadataPort.savedVerificationData,
            )
            assertEquals(1, cloudSyncPort.count("uploadVaultMetadata"))
            assertEquals(listOf(false), startCloudSyncListeningUseCase.calls)
            assertEquals(0, cloudSyncPort.count("startListening"))
            assertEquals(0, bootstrapCloudVaultFromLocalUseCase.callCount)
        }

    @Test
    fun `already matching unlocks starts cloud sync and hides sheet`() =
        runTest {
            val sheet = MutableStateFlow<VaultReauthSheetState>(VaultReauthSheetState.Hidden)
            detectVaultReconciliationStateUseCase.result =
                Result.success(VaultReconciliationOutcome.AlreadyMatching)

            delegate.onSubmitVaultReauthPassword(this, "Settings", sheet, "password")
            advanceUntilIdle()

            assertEquals(VaultReauthSheetState.Hidden, sheet.value)
            assertEquals(listOf("detect", "unlock", "startSync"), vaultFlowEvents)
            assertEquals(0, bootstrapCloudVaultFromLocalUseCase.callCount)
            assertTrue(createVaultUseCase.calls.isEmpty())
        }

    @Test
    fun `password mismatch shows inline error and keeps sheet open`() =
        runTest {
            val sheet = MutableStateFlow<VaultReauthSheetState>(VaultReauthSheetState.Hidden)
            detectVaultReconciliationStateUseCase.result =
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
            assertTrue(unlockVaultFromStoredMetadataUseCase.calls.isEmpty())
            assertTrue(startCloudSyncListeningUseCase.calls.isEmpty())
        }

    @Test
    fun `sync start failure keeps reauth sheet open with error`() =
        runTest {
            val sheet = MutableStateFlow<VaultReauthSheetState>(VaultReauthSheetState.Hidden)
            detectVaultReconciliationStateUseCase.result =
                Result.success(VaultReconciliationOutcome.AlreadyMatching)
            startCloudSyncListeningUseCase.result = Result.failure(IllegalStateException("replay failed"))

            delegate.onSubmitVaultReauthPassword(this, "Settings", sheet, "password")
            advanceUntilIdle()

            assertEquals(
                VaultReauthSheetState.EnterPassword(
                    isUnlocking = false,
                    errorMessage = "Failed to start cloud sync",
                ),
                sheet.value,
            )
            assertEquals(listOf(true), startCloudSyncListeningUseCase.calls)
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
            cloudSyncPort.syncRemoteResult =
                Result.success(
                    com.browntowndev.enclave.domain.model.sync.CloudSyncEventReplayResult(
                        eventsApplied = 0,
                        lastAppliedSequence = 0,
                        hasMore = false,
                    ),
                )
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

            detectVaultReconciliationStateUseCase.result =
                Result.success(VaultReconciliationOutcome.AlreadyMatching)
            vaultMetadataPort.salt = "salt-b64"
            vaultMetadataPort.wrappedDek = "wrapped-b64"
            cryptoManager.decodeBase64Override = { encoded ->
                when (encoded) {
                    "salt-b64" -> salt
                    "wrapped-b64" -> wrappedDek
                    else -> ByteArray(0)
                }
            }

            realDelegate.onSubmitVaultReauthPassword(this, "Settings", sheet, "password")
            advanceUntilIdle()

            assertEquals(VaultReauthSheetState.Hidden, sheet.value)
            assertEquals(listOf("password"), unlockVaultFromStoredMetadataUseCase.calls)
            // Reauth path must drive upload -> replay -> live listener in order.
            assertEquals(
                listOf(
                    "syncAll",
                    "getRemainingEventsCount",
                    "syncRemote",
                    "bootstrapFeatureSettings",
                    "startListening",
                ),
                cloudSyncPort.calls.filter {
                    it in
                        setOf(
                            "syncAll",
                            "getRemainingEventsCount",
                            "syncRemote",
                            "bootstrapFeatureSettings",
                            "startListening",
                        )
                },
            )

            detectVaultReconciliationStateUseCase.result = Result.success(payload)

            realDelegate.onSubmitVaultReauthPassword(this, "Settings", sheet, "password")
            advanceUntilIdle()
            realDelegate.onConfirmAdoptCloud(this, "Settings", sheet)
            advanceUntilIdle()

            // Adoption path must skip upload but still drive replay -> live listener.
            assertEquals(listOf(payload), adoptCloudVaultUseCase.calls)
            assertTrue(cloudSyncPort.count("syncRemote") >= 1)
            assertTrue(cloudSyncPort.count("startListening") >= 1)
        }
}
