package com.browntowndev.enclave.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.model.config.MediaCapability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class UiSelectionRepositoryImplTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun selectedChatMode_defaultsToFastWhenNothingStored() =
        runTest {
            val repository = UiSelectionRepositoryImpl(createDataStore(backgroundScope))

            assertEquals(Mode.FAST, repository.selectedChatMode.first())
        }

    @Test
    fun setChatMode_roundTripsAndEmitsNewValue() =
        runTest {
            val repository = UiSelectionRepositoryImpl(createDataStore(backgroundScope))

            repository.setChatMode(Mode.THINKING)

            assertEquals(Mode.THINKING, repository.selectedChatMode.first())
        }

    @Test
    fun selectedChatMode_fallsBackToDefaultForCorruptedValue() =
        runTest {
            val dataStore = createDataStore(backgroundScope)
            dataStore.edit { it[stringPreferencesKey("ui_selected_chat_mode")] = "NOT_A_MODE" }
            val repository = UiSelectionRepositoryImpl(dataStore)

            assertEquals(Mode.FAST, repository.selectedChatMode.first())
        }

    @Test
    fun selectedStudioMediaType_defaultsToImageWhenNothingStored() =
        runTest {
            val repository = UiSelectionRepositoryImpl(createDataStore(backgroundScope))

            assertEquals(MediaCapability.IMAGE, repository.selectedStudioMediaType.first())
        }

    @Test
    fun setStudioMediaType_roundTripsAndEmitsNewValue() =
        runTest {
            val repository = UiSelectionRepositoryImpl(createDataStore(backgroundScope))

            repository.setStudioMediaType(MediaCapability.VIDEO)

            assertEquals(MediaCapability.VIDEO, repository.selectedStudioMediaType.first())
        }

    @Test
    fun setChatModeAndSetStudioMediaType_areIndependentKeys() =
        runTest {
            val repository = UiSelectionRepositoryImpl(createDataStore(backgroundScope))

            repository.setChatMode(Mode.COUNCIL)
            repository.setStudioMediaType(MediaCapability.MUSIC)

            assertEquals(Mode.COUNCIL, repository.selectedChatMode.first())
            assertEquals(MediaCapability.MUSIC, repository.selectedStudioMediaType.first())
        }

    private fun createDataStore(scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempDir.resolve("ui-selection-${System.nanoTime()}.preferences_pb").toFile() },
        )
}
