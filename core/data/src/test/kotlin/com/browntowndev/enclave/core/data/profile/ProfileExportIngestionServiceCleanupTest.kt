package com.browntowndev.enclave.core.data.profile

import com.browntowndev.enclave.core.data.local.ProfileAnalysisRunDao
import com.browntowndev.enclave.core.data.local.ProfileCorpusDao
import com.browntowndev.enclave.core.data.local.ProfileFindingsDao
import com.browntowndev.enclave.core.data.profile.parse.AnthropicExportParser
import com.browntowndev.enclave.core.data.profile.parse.GenericFallbackParser
import com.browntowndev.enclave.core.data.profile.parse.GoogleExportParser
import com.browntowndev.enclave.core.data.profile.parse.OpenAiExportParser
import com.browntowndev.enclave.core.data.profile.parse.XaiExportParser
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class ProfileExportIngestionServiceCleanupTest {
    private val corpusDao = mockk<ProfileCorpusDao>(relaxed = true)
    private val findingsDao = mockk<ProfileFindingsDao>(relaxed = true)
    private val runDao = mockk<ProfileAnalysisRunDao>(relaxed = true)
    private val context = mockk<android.content.Context>(relaxed = true)
    private lateinit var service: ProfileExportIngestionService

    @BeforeEach
    fun setup() {
        every { context.cacheDir } returns File(System.getProperty("java.io.tmpdir") ?: "/tmp")
        service =
            ProfileExportIngestionService(
                context = context,
                dao = corpusDao,
                findingsDao = findingsDao,
                runDao = runDao,
                openAiParser = OpenAiExportParser(),
                anthropicParser = AnthropicExportParser(),
                googleParser = GoogleExportParser(),
                xaiParser = XaiExportParser(),
                fallbackParser = GenericFallbackParser(),
            )
    }

    @Test
    fun `PP-130 clearCorpus deletes messages and conversations`() =
        runTest {
            service.clearCorpus("run-1")

            coVerify { corpusDao.deleteMessagesForRun("run-1") }
            coVerify { corpusDao.deleteConversationsForRun("run-1") }
        }

    @Test
    fun `PP-131 clearAll deletes corpus plus findings and run state`() =
        runTest {
            service.clearAll("run-2")

            coVerify { corpusDao.deleteMessagesForRun("run-2") }
            coVerify { corpusDao.deleteConversationsForRun("run-2") }
            coVerify { findingsDao.deleteAll("run-2") }
            coVerify { runDao.delete("run-2") }
        }

    @Test
    fun `PP-132 clearAll deletes findings independently of clearCorpus`() =
        runTest {
            // clearCorpus should NOT delete findings
            service.clearCorpus("run-3")
            coVerify(exactly = 0) { findingsDao.deleteAll("run-3") }

            // clearAll SHOULD delete findings
            service.clearAll("run-3")
            coVerify(exactly = 1) { findingsDao.deleteAll("run-3") }
        }
}
