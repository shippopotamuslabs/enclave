package com.browntowndev.enclave.core.data.profile

import android.content.Context
import android.net.Uri
import com.browntowndev.enclave.core.data.local.ProfileCorpusConversationEntity
import com.browntowndev.enclave.core.data.local.ProfileCorpusDao
import com.browntowndev.enclave.core.data.local.ProfileCorpusMessageEntity
import com.browntowndev.enclave.core.data.profile.parse.GenericFallbackParser
import com.browntowndev.enclave.core.data.profile.parse.ProviderExportDetector
import com.browntowndev.enclave.domain.model.profile.ConversationRecord
import com.browntowndev.enclave.domain.model.profile.ParseHealthReport
import com.browntowndev.enclave.domain.port.profile.ConversationExportParser
import com.browntowndev.enclave.domain.port.profile.ProfileExportIngestionPort
import com.browntowndev.enclave.domain.util.JTokkitTokenCounter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_ENTRY_CHARS = 10 * 1024 * 1024

@Singleton
@Suppress("MagicNumber")
class ProfileExportIngestionService
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: ProfileCorpusDao,
        private val findingsDao: com.browntowndev.enclave.core.data.local.ProfileFindingsDao,
        private val runDao: com.browntowndev.enclave.core.data.local.ProfileAnalysisRunDao,
        private val openAiParser: com.browntowndev.enclave.core.data.profile.parse.OpenAiExportParser,
        private val anthropicParser: com.browntowndev.enclave.core.data.profile.parse.AnthropicExportParser,
        private val googleParser: com.browntowndev.enclave.core.data.profile.parse.GoogleExportParser,
        private val xaiParser: com.browntowndev.enclave.core.data.profile.parse.XaiExportParser,
        private val fallbackParser: GenericFallbackParser,
    ) : ProfileExportIngestionPort {
        private val detector by lazy {
            ProviderExportDetector(
                parsers = listOf(openAiParser, anthropicParser, googleParser, xaiParser),
                fallback = fallbackParser,
            )
        }

        override suspend fun ingestZipFiles(
            uris: List<String>,
            corpusRunId: String,
        ): List<ParseHealthReport> =
            withContext(Dispatchers.IO) {
                val reports = mutableListOf<ParseHealthReport>()
                for (uri in uris) {
                    reports.addAll(ingestSingleZip(uri, corpusRunId))
                }
                reports
            }

        override suspend fun clearCorpus(corpusRunId: String) =
            withContext(Dispatchers.IO) {
                dao.deleteMessagesForRun(corpusRunId)
                dao.deleteConversationsForRun(corpusRunId)
                tempDirForRun(corpusRunId).deleteRecursively()
                Unit
            }

        override suspend fun clearAll(corpusRunId: String) =
            withContext(Dispatchers.IO) {
                dao.deleteMessagesForRun(corpusRunId)
                dao.deleteConversationsForRun(corpusRunId)
                findingsDao.deleteAll(corpusRunId)
                runDao.delete(corpusRunId)
                tempDirForRun(corpusRunId).deleteRecursively()
                Unit
            }

        private suspend fun ingestSingleZip(uri: String, corpusRunId: String): List<ParseHealthReport> {
            val reports = mutableListOf<ParseHealthReport>()
            val androidUri = Uri.parse(uri)
            val stream = context.contentResolver.openInputStream(androidUri) ?: return reports

            ZipInputStream(stream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val entryName = entry.name.substringAfterLast('/')
                        val content = readEntryText(zip)
                        val preview = content.take(ProviderExportDetector.PREVIEW_CHARS)
                        val parser = detector.detect(entryName, preview)
                        if (parser != null) {
                            val (records, report) = parser.parse(entryName, content)
                            persistRecords(records, corpusRunId)
                            reports.add(report)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            return reports
        }

        private fun readEntryText(zip: ZipInputStream): String {
            val reader = InputStreamReader(zip, Charsets.UTF_8)
            val builder = StringBuilder()
            val buffer = CharArray(8192)
            var totalChars = 0
            while (true) {
                val read = reader.read(buffer)
                if (read == -1) break
                totalChars += read
                if (totalChars > MAX_ENTRY_CHARS) {
                    throw IllegalStateException("Zip entry exceeds safety size limit of 10MB to prevent Out-Of-Memory errors.")
                }
                builder.append(buffer, 0, read)
            }
            return builder.toString()
        }

        private suspend fun persistRecords(records: List<ConversationRecord>, corpusRunId: String) {
            for (record in records) {
                val conversationId = java.util.UUID.randomUUID().toString()
                val userText = record.userMessages.joinToString(" ") { it.content }
                val assistantText = record.assistantMessages.joinToString(" ") { it.content }
                val userTokens = JTokkitTokenCounter.countTokens(userText)
                val assistantTokens = JTokkitTokenCounter.countTokens(assistantText)

                val seedText = buildString {
                    append(record.title)
                    record.userMessages.firstOrNull()?.let { append(" ").append(it.content.take(300)) }
                }

                val conversationEntity = ProfileCorpusConversationEntity(
                    id = conversationId,
                    corpusRunId = corpusRunId,
                    externalId = record.externalId,
                    title = record.title,
                    provider = record.provider.name,
                    createdAtMs = record.createdAtMs,
                    userTokenCount = userTokens,
                    assistantTokenCount = assistantTokens,
                    messageCount = record.messages.size,
                    userMessageCount = record.userMessages.size,
                    embeddingSeedText = seedText,
                )
                dao.insertConversation(conversationEntity)

                val messageEntities = record.messages.map { msg ->
                    ProfileCorpusMessageEntity(
                        conversationId = conversationId,
                        corpusRunId = corpusRunId,
                        role = msg.role.name,
                        content = msg.content,
                        createdAtMs = msg.createdAtMs,
                        orderIndex = msg.orderIndex,
                    )
                }
                dao.insertMessages(messageEntities)
            }
        }

        private fun tempDirForRun(corpusRunId: String): File =
            File(context.cacheDir, "profile_corpus_$corpusRunId")
    }
