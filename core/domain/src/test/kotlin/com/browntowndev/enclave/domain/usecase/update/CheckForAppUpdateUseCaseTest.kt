package com.browntowndev.enclave.domain.usecase.update

import com.browntowndev.enclave.core.testing.FakeInstalledAppVersionPort
import com.browntowndev.enclave.core.testing.FakeUpdateCheckPreferencesPort
import com.browntowndev.enclave.core.testing.FakeUpdateMetadataPort
import com.browntowndev.enclave.core.testing.TestClock
import com.browntowndev.enclave.domain.model.update.LatestRelease
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("CheckForAppUpdateUseCase")
class CheckForAppUpdateUseCaseTest {
    private val clock = TestClock()
    private val metadata = FakeUpdateMetadataPort()
    private val installed = FakeInstalledAppVersionPort()
    private val preferences = FakeUpdateCheckPreferencesPort()
    private lateinit var useCase: CheckForAppUpdateUseCase

    private fun release(versionCode: Int): LatestRelease =
        LatestRelease(
            versionCode = versionCode,
            versionName = "1.$versionCode.0",
            apkFileName = "enclave-v1.$versionCode.0.apk",
            downloadUrl = "https://github.com/shippopotamuslabs/enclave/releases/latest/download/enclave-v1.$versionCode.0.apk",
        )

    @BeforeEach
    fun setup() {
        clock.setTime(1_000_000_000L)
        installed.installedVersionCode = 5
        useCase = CheckForAppUpdateUseCase(metadata, installed, preferences, clock)
    }

    @Test
    fun `recent check returns None without fetching`() =
        runTest {
            preferences.setLastCheckMillis(clock.currentTimeMillis() - 1_000L)
            metadata.release = release(6)

            val result = useCase()

            assertEquals(AppUpdateResult.None, result)
            assertEquals(0, metadata.fetchCount)
        }

    @Test
    fun `check at exactly the interval fetches again`() =
        runTest {
            preferences.setLastCheckMillis(clock.currentTimeMillis() - CheckForAppUpdateUseCase.CHECK_INTERVAL_MILLIS)
            metadata.release = release(6)

            val result = useCase()

            assertTrue(result is AppUpdateResult.UpdateAvailable)
            assertEquals(1, metadata.fetchCount)
        }

    @Test
    fun `newer release returns UpdateAvailable`() =
        runTest {
            metadata.release = release(6)

            val result = useCase()

            assertTrue(result is AppUpdateResult.UpdateAvailable)
            assertEquals(release(6), (result as AppUpdateResult.UpdateAvailable).release)
        }

    @Test
    fun `equal version returns None`() =
        runTest {
            metadata.release = release(5)

            assertEquals(AppUpdateResult.None, useCase())
        }

    @Test
    fun `older version returns None`() =
        runTest {
            metadata.release = release(4)

            assertEquals(AppUpdateResult.None, useCase())
        }

    @Test
    fun `dismissed version returns None`() =
        runTest {
            metadata.release = release(6)
            preferences.setDismissedVersionCode(6)

            assertEquals(AppUpdateResult.None, useCase())
        }

    @Test
    fun `fetch failure returns None and still records the check`() =
        runTest {
            metadata.release = null

            assertEquals(AppUpdateResult.None, useCase())
            assertEquals(1, metadata.fetchCount)
            assertEquals(clock.currentTimeMillis(), preferences.lastCheckMillis())
        }

    @Test
    fun `successful check records the check time`() =
        runTest {
            metadata.release = release(6)

            useCase()

            assertEquals(clock.currentTimeMillis(), preferences.lastCheckMillis())
        }
}
