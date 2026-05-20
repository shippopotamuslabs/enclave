package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.port.repository.CloudSyncPort
import com.browntowndev.pocketcrew.domain.port.security.CryptoManagerPort
import com.browntowndev.pocketcrew.domain.port.security.VaultMetadataPort
import com.browntowndev.pocketcrew.domain.usecase.security.AdoptCloudVaultUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.BootstrapCloudVaultFromLocalUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.CreateVaultUseCase
import com.browntowndev.pocketcrew.domain.usecase.security.DetectVaultReconciliationStateUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAuthUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsPreferencesUseCases
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TosSheetTest {
    private val preferencesUseCases = mockk<SettingsPreferencesUseCases>(relaxed = true)
    private val authUseCases = mockk<SettingsAuthUseCases>(relaxed = true)
    private val errorHandler = mockk<ViewModelErrorHandler>()
    private val detectVaultReconciliationStateUseCase = mockk<DetectVaultReconciliationStateUseCase>()
    private val adoptCloudVaultUseCase = mockk<AdoptCloudVaultUseCase>(relaxed = true)
    private val bootstrapCloudVaultFromLocalUseCase = mockk<BootstrapCloudVaultFromLocalUseCase>(relaxed = true)
    private val createVaultUseCase = mockk<CreateVaultUseCase>()
    private val cryptoManager = mockk<CryptoManagerPort>()
    private val vaultMetadataPort = mockk<VaultMetadataPort>()
    private val cloudSyncPort = mockk<CloudSyncPort>()

    private lateinit var delegate: SettingsGeneralDelegate
    private lateinit var sheetVisibility: MutableStateFlow<SheetVisibilityState>

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
