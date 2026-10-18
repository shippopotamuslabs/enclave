package com.browntowndev.enclave.core.data.profile

import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.data.FakeProfileAnalysisRunDao
import com.browntowndev.enclave.core.data.FakeProfileCorpusDao
import com.browntowndev.enclave.core.data.FakeProfileFindingsDao
import com.browntowndev.enclave.core.data.profile.parse.AnthropicExportParser
import com.browntowndev.enclave.core.data.profile.parse.GenericFallbackParser
import com.browntowndev.enclave.core.data.profile.parse.GoogleExportParser
import com.browntowndev.enclave.core.data.profile.parse.OpenAiExportParser
import com.browntowndev.enclave.core.data.profile.parse.XaiExportParser
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ProfileExportIngestionServiceCleanupTest {
    private val corpusDao = FakeProfileCorpusDao()
    private val findingsDao = FakeProfileFindingsDao()
    private val runDao = FakeProfileAnalysisRunDao()
    private lateinit var service: ProfileExportIngestionService

    @BeforeEach
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
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

            assertEquals(listOf("run-1"), corpusDao.deleteMessagesForRunCalls)
            assertEquals(listOf("run-1"), corpusDao.deleteConversationsForRunCalls)
        }

    @Test
    fun `PP-131 clearAll deletes corpus plus findings and run state`() =
        runTest {
            service.clearAll("run-2")

            assertEquals(listOf("run-2"), corpusDao.deleteMessagesForRunCalls)
            assertEquals(listOf("run-2"), corpusDao.deleteConversationsForRunCalls)
            assertEquals(listOf("run-2"), findingsDao.deleteAllCalls)
            assertEquals(listOf("run-2"), runDao.deleteCalls)
        }

    @Test
    fun `PP-132 clearAll deletes findings independently of clearCorpus`() =
        runTest {
            // clearCorpus should NOT delete findings
            service.clearCorpus("run-3")
            assertTrue(findingsDao.deleteAllCalls.isEmpty())

            // clearAll SHOULD delete findings
            service.clearAll("run-3")
            assertEquals(listOf("run-3"), findingsDao.deleteAllCalls)
        }
}
