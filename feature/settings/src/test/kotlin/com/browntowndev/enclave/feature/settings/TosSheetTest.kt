package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.ui.component.sheet.TOS_TEXT
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.port.repository.CloudSyncPort
import com.browntowndev.enclave.domain.port.security.CryptoManagerPort
import com.browntowndev.enclave.domain.port.security.VaultMetadataPort
import com.browntowndev.enclave.domain.usecase.security.AdoptCloudVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.BootstrapCloudVaultFromLocalUseCase
import com.browntowndev.enclave.domain.usecase.security.CreateVaultUseCase
import com.browntowndev.enclave.domain.usecase.security.DetectVaultReconciliationStateUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsAuthUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsPreferencesUseCases
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TosSheetTest {
    private val preferencesUseCases = FakeSettingsPreferencesUseCases()
    private val authUseCases = FakeSettingsAuthUseCases()
    private val errorHandler = FakeViewModelErrorHandler()
    private val detectVaultReconciliationStateUseCase = FakeDetectVaultReconciliationStateUseCase()
    private val adoptCloudVaultUseCase = FakeAdoptCloudVaultUseCase()
    private val bootstrapCloudVaultFromLocalUseCase = FakeBootstrapCloudVaultFromLocalUseCase()
    private val createVaultUseCase = FakeCreateVaultUseCase()
    private val cryptoManager = com.browntowndev.enclave.core.testing.FakeCryptoManagerPort()
    private val vaultMetadataPort = com.browntowndev.enclave.core.testing.FakeVaultMetadataPort()
    private val cloudSyncPort = com.browntowndev.enclave.core.testing.FakeCloudSyncPort()

    private lateinit var delegate: SettingsGeneralDelegate
    private lateinit var sheetVisibility: MutableStateFlow<SheetVisibilityState>

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
                unlockVaultFromStoredMetadataUseCase = FakeUnlockVaultFromStoredMetadataUseCase(),
                startCloudSyncListeningUseCase = FakeStartCloudSyncListeningUseCase(),
                cryptoManager = cryptoManager,
                vaultMetadataPort = vaultMetadataPort,
                cloudSyncPort = cloudSyncPort,
            )
        sheetVisibility = MutableStateFlow(SheetVisibilityState())
    }

    // S1: TOS sheet opens when onShowTosSheet(true) is called
    @Test
    fun `S1 onShowTosSheet true sets tos visibility to true`() {
        delegate.onShowTosSheet(sheetVisibility, show = true)

        assertTrue(sheetVisibility.value.tos)
    }

    // S2: TOS sheet closes when onShowTosSheet(false) is called
    @Test
    fun `S2 onShowTosSheet false clears tos visibility`() {
        sheetVisibility.value = SheetVisibilityState(tos = true)

        delegate.onShowTosSheet(sheetVisibility, show = false)

        assertFalse(sheetVisibility.value.tos)
    }

    // S3: TOS sheet is not shown by default
    @Test
    fun `S3 SheetVisibilityState defaults tos to false`() {
        assertFalse(SheetVisibilityState().tos)
    }

    @Test
    fun `S3 SettingsHomeUiState defaults isTosSheetOpen to false`() {
        assertFalse(SettingsHomeUiState().isTosSheetOpen)
    }

    @Test
    fun `S3 SettingsUiState home defaults isTosSheetOpen to false`() {
        assertFalse(SettingsUiState().home.isTosSheetOpen)
    }

    // S4: TOS content contains required legal sections
    @Test
    fun `S4 TOS text contains as-is warranty disclaimer`() {
        assertTrue(TOS_TEXT.contains("as is", ignoreCase = true))
    }

    @Test
    fun `S4 TOS text contains limitation of liability section`() {
        assertTrue(TOS_TEXT.contains("limitation of liability", ignoreCase = true))
    }

    @Test
    fun `S4 TOS text contains age requirement of 18`() {
        assertTrue(TOS_TEXT.contains("18"))
    }

    @Test
    fun `S4 TOS text contains API key user responsibility`() {
        assertTrue(TOS_TEXT.contains("api key", ignoreCase = true))
    }

    @Test
    fun `S4 TOS text contains governing law section`() {
        assertTrue(TOS_TEXT.contains("governing law", ignoreCase = true))
    }

    @Test
    fun `S4 TOS text contains indemnification clause`() {
        assertTrue(TOS_TEXT.contains("indemnif", ignoreCase = true))
    }

    @Test
    fun `S4 TOS text contains acceptable use section`() {
        assertTrue(TOS_TEXT.contains("acceptable use", ignoreCase = true))
    }

    // M1: Mutation defense — sheet cannot be permanently open by default
    @Test
    fun `M1 SheetVisibilityState tos always defaults to false`() {
        repeat(1000) { iteration ->
            val state = SheetVisibilityState()
            assertEquals(
                false,
                state.tos,
                "SheetVisibilityState().tos was true at iteration $iteration",
            )
        }
    }

    // M2: Mutation defense — SettingsHomeUiState isTosSheetOpen always defaults false
    @Test
    fun `M2 SettingsHomeUiState isTosSheetOpen always defaults to false`() {
        assertFalse(SettingsHomeUiState().isTosSheetOpen)
    }

    @Test
    fun `M2 SettingsUiState home isTosSheetOpen always defaults to false`() {
        assertFalse(SettingsUiState().home.isTosSheetOpen)
    }
}
