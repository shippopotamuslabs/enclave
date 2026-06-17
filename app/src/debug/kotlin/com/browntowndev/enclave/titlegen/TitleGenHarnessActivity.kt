package com.browntowndev.enclave.titlegen

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.browntowndev.enclave.domain.port.inference.ChatTitleGeneratorPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import com.browntowndev.enclave.feature.inference.titlegen.TitleGenHarnessDiagnostics
import com.browntowndev.enclave.feature.inference.titlegen.TitleGenHarnessSeed
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Debug-only adb harness for the on-device title generator.
 *
 * ```
 * adb shell am start -n com.browntowndev.enclave/.titlegen.TitleGenHarnessActivity \
 *   -e prompt "hello"
 * adb logcat -d -s TitleGenHarness:I OnnxChatTitleGen:D
 * ```
 */
@AndroidEntryPoint
class TitleGenHarnessActivity : ComponentActivity() {
    @Inject
    lateinit var titleGenerator: ChatTitleGeneratorPort

    @Inject
    lateinit var utilityModelFilePort: UtilityModelFilePort

    @Inject
    lateinit var utilityModelRepository: UtilityModelRepositoryPort

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prompt = intent.getStringExtra(EXTRA_PROMPT) ?: DEFAULT_PROMPT
        val exitCode =
            runBlocking(Dispatchers.IO) {
                TitleGenHarnessSeed.ensureRegistry(utilityModelRepository)
                TitleGenHarnessSeed.ensureAssets(applicationContext)
                Log.i(TitleGenHarnessDiagnostics.LOG_TAG, "Registry seeded; staged assets copied if needed")
                val report =
                    TitleGenHarnessDiagnostics.run(
                        context = applicationContext,
                        utilityModelFilePort = utilityModelFilePort,
                        userPrompt = prompt,
                        portGenerateTitle = { userPrompt -> titleGenerator.generateTitle(userPrompt) },
                    )
                report.toLogString().lines().forEach { line ->
                    Log.i(TitleGenHarnessDiagnostics.LOG_TAG, line)
                }
                if (report.sanitizedTitle != null) 0 else 1
            }
        setResult(if (exitCode == 0) RESULT_OK else RESULT_FIRST_USER)
        finish()
    }

    companion object {
        const val EXTRA_PROMPT = "prompt"
        private const val DEFAULT_PROMPT = "How do I use rememberSaveable in Compose?"

        fun launchIntent(
            prompt: String = DEFAULT_PROMPT,
        ): Intent =
            Intent().apply {
                setClassName(
                    "com.browntowndev.enclave",
                    "com.browntowndev.enclave.titlegen.TitleGenHarnessActivity",
                )
                putExtra(EXTRA_PROMPT, prompt)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}