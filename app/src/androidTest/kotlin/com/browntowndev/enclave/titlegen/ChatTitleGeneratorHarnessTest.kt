package com.browntowndev.enclave.titlegen

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.browntowndev.enclave.domain.port.inference.ChatTitleGeneratorPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import com.browntowndev.enclave.feature.inference.titlegen.TitleGenHarnessDiagnostics
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device/emulator harness for the on-device chat title generator.
 *
 * Install and run:
 * ```
 * ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
 * adb install -r app/build/outputs/apk/debug/app-debug.apk
 * adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
 * adb shell am instrument -w \
 *   -e class com.browntowndev.enclave.titlegen.ChatTitleGeneratorHarnessTest \
 *   com.browntowndev.enclave.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * Optional prompt override:
 * `-e prompt "debug 500 errors in production"`
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ChatTitleGeneratorHarnessTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var titleGenerator: ChatTitleGeneratorPort

    @Inject
    lateinit var utilityModelFilePort: UtilityModelFilePort

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun runDefaultPromptHarness() =
        runBlocking {
            val prompt =
                InstrumentationRegistry.getArguments().getString("prompt")
                    ?: "How do I use rememberSaveable in Compose?"
            runHarness(prompt)
        }

    @Test
    fun runShortGreetingHarness() =
        runBlocking {
            runHarness("hello")
        }

    @Test
    fun runDebug500Harness() =
        runBlocking {
            runHarness("debug 500 errors in production")
        }

    private suspend fun runHarness(prompt: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report =
            TitleGenHarnessDiagnostics.run(
                context = context,
                utilityModelFilePort = utilityModelFilePort,
                userPrompt = prompt,
                portGenerateTitle = { userPrompt -> titleGenerator.generateTitle(userPrompt) },
            )
        report.toLogString().lines().forEach { line ->
            Log.i(TitleGenHarnessDiagnostics.LOG_TAG, line)
        }
        if (report.sanitizedTitle == null) {
            fail("Title generation failed. Full harness report:\n${report.toLogString()}")
        }
        assertNotNull(report.portGenerateTitle)
    }
}