package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.memory.MemoryCategory
import com.browntowndev.enclave.domain.model.settings.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FakesTest {
    @Test
    fun fakeSettings_updatesFlowAndHonoursFailureToggle() = runTest {
        val fake = FakeSettingsRepository()
        fake.updateTheme(AppTheme.DARK)
        assertEquals(AppTheme.DARK, fake.settingsFlow.first().theme)

        fake.shouldFail = true
        val failed = runCatching { fake.updateTheme(AppTheme.LIGHT) }.isFailure
        assertTrue(failed)
        assertEquals(AppTheme.DARK, fake.settingsFlow.first().theme)
    }

    @Test
    fun fakeMemories_insertUpdateAndCoreFilter() = runTest {
        val fake = FakeMemoriesRepository()
        val inserted = fake.insertMemory(MemoryCategory.PSYCH_PROFILE, "profile")
        fake.insertMemory(MemoryCategory.FACTS, "fact")

        assertEquals(listOf(inserted), fake.getCoreMemories())

        fake.updateMemory(inserted.id, "updated")
        assertEquals("updated", fake.getMemoryById(inserted.id)?.content)
    }

    @Test
    fun fakeApiModel_seedAndQueryConfigurations() = runTest {
        val fake = FakeApiModelRepository()
        assertTrue(fake.observeAllConfigurations().first().isEmpty())
        assertTrue(fake.getAllCredentials().isEmpty())
    }
}
