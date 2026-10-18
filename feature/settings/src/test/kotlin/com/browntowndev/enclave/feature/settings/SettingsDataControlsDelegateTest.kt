package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.usecase.settings.DeleteAccountUseCase
import com.browntowndev.enclave.domain.usecase.settings.DeleteAllArtifactsUseCase
import com.browntowndev.enclave.domain.usecase.settings.DeleteAllChatsUseCase
import com.browntowndev.enclave.domain.usecase.settings.DeleteAllMediaUseCase
import com.browntowndev.enclave.domain.usecase.settings.DeleteAllMemoriesUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsDataControlsUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsPreferencesUseCases
import com.browntowndev.enclave.domain.usecase.settings.UpdateAutomaticErrorTrackingEnabledUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class SettingsDataControlsDelegateTest {
    private val deleteAllChats = FakeDeleteAllChatsUseCase()
    private val deleteAllMedia = FakeDeleteAllMediaUseCase()
    private val deleteAllMemories = FakeDeleteAllMemoriesUseCase()
    private val deleteAllArtifacts = FakeDeleteAllArtifactsUseCase()
    private val deleteAccount = FakeDeleteAccountUseCase()
    private val useCases =
        FakeSettingsDataControlsUseCases(
            deleteAllChats = deleteAllChats,
            deleteAllMedia = deleteAllMedia,
            deleteAllMemories = deleteAllMemories,
            deleteAllArtifacts = deleteAllArtifacts,
            deleteAccount = deleteAccount,
        )

    private val errorHandler = FakeViewModelErrorHandler()
    private val updateAutomaticErrorTrackingEnabledUseCase = FakeUpdateAutomaticErrorTrackingEnabledUseCase()
    private val preferencesUseCases =
        FakeSettingsPreferencesUseCases(
            updateAutomaticErrorTrackingEnabled = updateAutomaticErrorTrackingEnabledUseCase,
        )

    private lateinit var delegate: SettingsDataControlsDelegate

    @BeforeEach
    fun setup() {
        delegate = SettingsDataControlsDelegate(useCases, preferencesUseCases, errorHandler)
    }

    @Test
    fun `opening sheet sets visibility`() =
        runTest {
            val sheet = MutableStateFlow(SheetVisibilityState())
            val dc = MutableStateFlow(DataControlsTransientState())
            delegate.onShowDataControlsSheet(sheet, dc, show = true)
            assertTrue(sheet.value.dataControls)
        }

    @Test
    fun `closing sheet resets transient state`() =
        runTest {
            val sheet = MutableStateFlow(SheetVisibilityState(dataControls = true))
            val dc =
                MutableStateFlow(
                    DataControlsTransientState(
                        pendingAction = DataControlsPendingAction.DeleteChats,
                        isDeleting = true,
                    ),
                )
            delegate.onShowDataControlsSheet(sheet, dc, show = false)
            assertFalse(sheet.value.dataControls)
            assertEquals(DataControlsTransientState(), dc.value)
        }

    @Test
    fun `request delete chats sets pending action`() {
        val dc = MutableStateFlow(DataControlsTransientState())
        delegate.onRequestDeleteAllChats(dc)
        assertEquals(DataControlsPendingAction.DeleteChats, dc.value.pendingAction)
        assertFalse(dc.value.isDeleting)
    }

    @Test
    fun `dismiss confirmation clears pending action`() {
        val dc = MutableStateFlow(DataControlsTransientState(pendingAction = DataControlsPendingAction.DeleteChats))
        delegate.onDismissDataControlsConfirmation(dc)
        assertNull(dc.value.pendingAction)
        assertFalse(dc.value.isDeleting)
    }

    @Test
    fun `dismiss confirmation is no-op while deleting`() {
        val dc =
            MutableStateFlow(
                DataControlsTransientState(
                    pendingAction = DataControlsPendingAction.DeleteChats,
                    isDeleting = true,
                ),
            )
        delegate.onDismissDataControlsConfirmation(dc)
        assertEquals(DataControlsPendingAction.DeleteChats, dc.value.pendingAction)
        assertTrue(dc.value.isDeleting)
    }

    @Test
    fun `confirm delete chats resets state on success`() =
        runTest {
            val dc = MutableStateFlow(DataControlsTransientState(pendingAction = DataControlsPendingAction.DeleteChats))
            delegate.onConfirmDeleteAllChats(this, "tag", dc)
            advanceUntilIdle()
            assertEquals(DataControlsTransientState(), dc.value)
            assertEquals(1, deleteAllChats.callCount)
        }

    @Test
    fun `confirm delete chats sets error on failure`() =
        runTest {
            deleteAllChats.failure = RuntimeException("db error")
            val dc = MutableStateFlow(DataControlsTransientState())
            delegate.onConfirmDeleteAllChats(this, "tag", dc)
            advanceUntilIdle()
            assertEquals("db error", dc.value.deleteError)
            assertNull(dc.value.pendingAction)
            assertFalse(dc.value.isDeleting)
            assertEquals(listOf("tag"), errorHandler.handledTags)
            assertEquals(listOf("Failed to delete all chats"), errorHandler.handledMessages)
            assertEquals(listOf("Couldn't delete chats"), errorHandler.handledUserMessages)
            assertTrue(errorHandler.handledThrowables.single() is RuntimeException)
            assertEquals("db error", errorHandler.handledThrowables.single().message)
        }

    @Test
    fun `confirm delete account handles Result failure`() =
        runTest {
            deleteAccount.result = Result.failure(Exception("purge failed"))
            val dc = MutableStateFlow(DataControlsTransientState())
            delegate.onConfirmDeleteAccount(this, "tag", dc)
            advanceUntilIdle()
            assertEquals("purge failed", dc.value.deleteError)
            assertNull(dc.value.pendingAction)
            assertFalse(dc.value.isDeleting)
            assertEquals(listOf("tag"), errorHandler.handledTags)
            assertEquals(listOf("Failed to delete account"), errorHandler.handledMessages)
            assertEquals(listOf("Couldn't delete account"), errorHandler.handledUserMessages)
            assertEquals("purge failed", errorHandler.handledThrowables.single().message)
        }

    @Test
    fun `confirm delete account clears deleting state when invoke throws`() =
        runTest {
            deleteAccount.failure = RuntimeException("sign-out failed")
            val dc = MutableStateFlow(DataControlsTransientState())
            delegate.onConfirmDeleteAccount(this, "tag", dc)
            advanceUntilIdle()
            assertEquals("sign-out failed", dc.value.deleteError)
            assertNull(dc.value.pendingAction)
            assertFalse(dc.value.isDeleting)
            assertEquals(listOf("tag"), errorHandler.handledTags)
            assertEquals(listOf("Failed to delete account"), errorHandler.handledMessages)
            assertEquals(listOf("Couldn't delete account"), errorHandler.handledUserMessages)
            assertTrue(errorHandler.handledThrowables.single() is RuntimeException)
            assertEquals("sign-out failed", errorHandler.handledThrowables.single().message)
        }
}
