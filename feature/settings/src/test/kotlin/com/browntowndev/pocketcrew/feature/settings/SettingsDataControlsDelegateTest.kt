package com.browntowndev.pocketcrew.feature.settings

import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.domain.usecase.settings.DeleteAccountUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.DeleteAllArtifactsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.DeleteAllChatsUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.DeleteAllMediaUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.DeleteAllMemoriesUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsDataControlsUseCases
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

    private val deleteAllChats = mockk<DeleteAllChatsUseCase>()
    private val deleteAllMedia = mockk<DeleteAllMediaUseCase>()
    private val deleteAllMemories = mockk<DeleteAllMemoriesUseCase>()
    private val deleteAllArtifacts = mockk<DeleteAllArtifactsUseCase>()
    private val deleteAccount = mockk<DeleteAccountUseCase>()
    private val useCases =
        mockk<SettingsDataControlsUseCases> {
            every { deleteAllChats } returns this@SettingsDataControlsDelegateTest.deleteAllChats
            every { deleteAllMedia } returns this@SettingsDataControlsDelegateTest.deleteAllMedia
            every { deleteAllMemories } returns this@SettingsDataControlsDelegateTest.deleteAllMemories
            every { deleteAllArtifacts } returns this@SettingsDataControlsDelegateTest.deleteAllArtifacts
            every { deleteAccount } returns this@SettingsDataControlsDelegateTest.deleteAccount
        }

    private val errorHandler = mockk<ViewModelErrorHandler>(relaxed = true)

    private lateinit var delegate: SettingsDataControlsDelegate

    @BeforeEach
    fun setup() {
        delegate = SettingsDataControlsDelegate(useCases, errorHandler)
        coEvery { deleteAllChats.invoke() } returns Unit
        coEvery { deleteAllMedia.invoke() } returns Unit
        coEvery { deleteAllMemories.invoke() } returns Unit
        coEvery { deleteAllArtifacts.invoke() } returns Unit
        coEvery { deleteAccount.invoke() } returns Result.success(Unit)
    }

    @Test
    fun `opening sheet sets visibility`() = runTest {
        val sheet = MutableStateFlow(SheetVisibilityState())
        val dc = MutableStateFlow(DataControlsTransientState())
        delegate.onShowDataControlsSheet(sheet, dc, show = true)
        assertTrue(sheet.value.dataControls)
    }

    @Test
    fun `closing sheet resets transient state`() = runTest {
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
    fun `confirm delete chats resets state on success`() = runTest {
        val dc = MutableStateFlow(DataControlsTransientState(pendingAction = DataControlsPendingAction.DeleteChats))
        delegate.onConfirmDeleteAllChats(this, "tag", dc)
        advanceUntilIdle()
        assertEquals(DataControlsTransientState(), dc.value)
        coVerify(exactly = 1) { deleteAllChats.invoke() }
    }

    @Test
    fun `confirm delete chats sets error on failure`() = runTest {
        coEvery { deleteAllChats.invoke() } throws RuntimeException("db error")
        val dc = MutableStateFlow(DataControlsTransientState())
        delegate.onConfirmDeleteAllChats(this, "tag", dc)
        advanceUntilIdle()
        assertEquals("db error", dc.value.deleteError)
        assertNull(dc.value.pendingAction)
        assertFalse(dc.value.isDeleting)
        verify(exactly = 1) {
            errorHandler.handleError(
                "tag",
                "Failed to delete all chats",
                match { it is RuntimeException && it.message == "db error" },
                "Couldn't delete chats",
            )
        }
    }

    @Test
    fun `confirm delete account handles Result failure`() = runTest {
        coEvery { deleteAccount.invoke() } returns Result.failure(Exception("purge failed"))
        val dc = MutableStateFlow(DataControlsTransientState())
        delegate.onConfirmDeleteAccount(this, "tag", dc)
        advanceUntilIdle()
        assertEquals("purge failed", dc.value.deleteError)
        assertNull(dc.value.pendingAction)
        assertFalse(dc.value.isDeleting)
        verify(exactly = 1) {
            errorHandler.handleError(
                "tag",
                "Failed to delete account",
                match { it is Exception && it.message == "purge failed" },
                "Couldn't delete account",
            )
        }
    }

    @Test
    fun `confirm delete account clears deleting state when invoke throws`() = runTest {
        coEvery { deleteAccount.invoke() } throws RuntimeException("sign-out failed")
        val dc = MutableStateFlow(DataControlsTransientState())
        delegate.onConfirmDeleteAccount(this, "tag", dc)
        advanceUntilIdle()
        assertEquals("sign-out failed", dc.value.deleteError)
        assertNull(dc.value.pendingAction)
        assertFalse(dc.value.isDeleting)
        verify(exactly = 1) {
            errorHandler.handleError(
                "tag",
                "Failed to delete account",
                match { it is RuntimeException && it.message == "sign-out failed" },
                "Couldn't delete account",
            )
        }
    }
}
