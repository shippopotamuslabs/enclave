package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.port.inference.ChatTitleGeneratorPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import javax.inject.Inject

/**
 * Generates a chat title on-device and applies it via [ChatRepository.renameChat].
 *
 * Runs asynchronously, in parallel with the main inference turn. The chat already has a
 * deterministic truncated title (set at creation time), so this use case only *upgrades*
 * it. If the generator returns `null` or low-quality output, the existing title is kept
 * (no-op) — there is no user-facing failure path.
 */
class GenerateChatTitleUseCase
    @Inject
    constructor(
        private val titleGenerator: ChatTitleGeneratorPort,
        private val chatRepository: ChatRepository,
        private val loggingPort: LoggingPort,
    ) {
        /**
         * @param chatId The chat whose title should be upgraded.
         * @param prompt The first user message text.
         */
        suspend operator fun invoke(
            chatId: ChatId,
            prompt: String,
        ) {
            if (prompt.isBlank()) return
            loggingPort.debug(TAG, "GenerateChatTitleUseCase invoked for chat: $chatId, promptLength=${prompt.length}")
            val start = System.currentTimeMillis()
            val raw =
                try {
                    titleGenerator.generateTitle(prompt)
                } catch (e: Exception) {
                    loggingPort.warning(TAG, "Title generation failed; keeping fallback: ${e.message}")
                    null
                }
            loggingPort.debug(TAG, "Generator returned raw title: '$raw'")
            val title = sanitize(raw)
            if (title == null) {
                loggingPort.debug(TAG, "Title generation produced no usable title; keeping fallback")
                return
            }
            loggingPort.debug(TAG, "Renaming chat $chatId to '$title'")
            chatRepository.renameChat(chatId, title)
            loggingPort.debug(TAG, "Chat title upgraded in ${System.currentTimeMillis() - start}ms (len=${title.length})")
        }

        /**
         * Cleans raw model output into a presentable title, or returns `null` if the output
         * is unusable (blank, only special-token artifacts, or implausibly long).
         */
        private fun sanitize(raw: String?): String? {
            if (raw == null) return null
            val cleaned =
                raw
                    .replace(SPECIAL_TOKEN_REGEX, " ")
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
                    .trim('"', '\'', '“', '”', '‘', '’')
                    .trim()
            if (cleaned.isBlank()) return null
            return if (cleaned.length > MAX_TITLE_LENGTH) {
                cleaned.take(MAX_TITLE_LENGTH).trimEnd() + "…"
            } else {
                cleaned
            }
        }

        private companion object {
            private const val TAG = "GenerateChatTitle"
            private const val MAX_TITLE_LENGTH = 60
            private val SPECIAL_TOKEN_REGEX = Regex("<[^>]*>")
            private val WHITESPACE_REGEX = Regex("\\s+")
        }
    }
