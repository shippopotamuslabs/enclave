package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.model.inference.XSearchParams

data class DeriveXSearchParamsResult(
    val params: XSearchParams?,
    val skipReason: String?,
)

class DeriveXSearchParamsFromPromptUseCase {
    private val stopWords =
        setOf(
            "a",
            "about",
            "above",
            "after",
            "again",
            "against",
            "all",
            "am",
            "an",
            "and",
            "any",
            "are",
            "arent",
            "as",
            "at",
            "be",
            "because",
            "been",
            "before",
            "being",
            "below",
            "between",
            "both",
            "but",
            "by",
            "cant",
            "cannot",
            "could",
            "couldnt",
            "did",
            "didnt",
            "do",
            "does",
            "doesnt",
            "doing",
            "dont",
            "down",
            "during",
            "each",
            "few",
            "for",
            "from",
            "further",
            "had",
            "hadnt",
            "has",
            "hasnt",
            "have",
            "havent",
            "having",
            "he",
            "hed",
            "hell",
            "hes",
            "her",
            "here",
            "heres",
            "hers",
            "herself",
            "him",
            "himself",
            "his",
            "how",
            "hows",
            "i",
            "id",
            "ill",
            "im",
            "ive",
            "if",
            "in",
            "into",
            "is",
            "isnt",
            "it",
            "its",
            "itself",
            "lets",
            "me",
            "more",
            "most",
            "mustnt",
            "my",
            "myself",
            "no",
            "nor",
            "not",
            "of",
            "off",
            "on",
            "once",
            "only",
            "or",
            "other",
            "ought",
            "our",
            "ours",
            "ourselves",
            "out",
            "over",
            "own",
            "same",
            "shant",
            "she",
            "shed",
            "shell",
            "shes",
            "should",
            "shouldnt",
            "so",
            "some",
            "such",
            "than",
            "that",
            "thats",
            "the",
            "their",
            "theirs",
            "them",
            "themselves",
            "then",
            "there",
            "theres",
            "these",
            "they",
            "theyd",
            "theyll",
            "theyre",
            "theyve",
            "this",
            "those",
            "through",
            "to",
            "too",
            "under",
            "until",
            "up",
            "very",
            "was",
            "wasnt",
            "we",
            "wed",
            "well",
            "were",
            "weve",
            "werent",
            "what",
            "whats",
            "when",
            "whens",
            "where",
            "wheres",
            "which",
            "while",
            "who",
            "whos",
            "whom",
            "why",
            "whys",
            "with",
            "wont",
            "would",
            "wouldnt",
            "you",
            "youd",
            "youll",
            "youre",
            "youve",
            "your",
            "yours",
            "yourself",
            "yourselves",
        )

    operator fun invoke(prompt: String): DeriveXSearchParamsResult {
        val wordCount = prompt.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val phraseRegex = "\"([^\"]+)\"".toRegex()
        val hashtagRegex = "#([a-zA-Z0-9_]+)".toRegex()
        val mentionRegex = "@([a-zA-Z0-9_]+)".toRegex()

        val hasPhrases = phraseRegex.containsMatchIn(prompt)
        val hasHashtags = hashtagRegex.containsMatchIn(prompt)
        val hasMentions = mentionRegex.containsMatchIn(prompt)

        val exceedsLength = wordCount > 30
        val hasEntities = hasPhrases || hasHashtags || hasMentions

        if (exceedsLength && !hasEntities) {
            return DeriveXSearchParamsResult(
                params = null,
                skipReason = "Conversational prompt too long without entities",
            )
        }

        val phrases =
            phraseRegex
                .findAll(prompt)
                .map { it.groupValues[1] }
                .toList()
                .distinct()
        val hashtags =
            hashtagRegex
                .findAll(prompt)
                .map { it.groupValues[1] }
                .toList()
                .distinct()
        val mentions =
            mentionRegex
                .findAll(prompt)
                .map { it.groupValues[1] }
                .toList()
                .distinct()

        // Strip quoted phrases, hashtags, mentions, and URLs from keyword search text
        val remainingText =
            prompt
                .replace(phraseRegex, " ")
                .replace(hashtagRegex, " ")
                .replace(mentionRegex, " ")
                .replace(Regex("https?://\\S+"), " ")

        // Strip punctuation and split into lowercase tokens
        val cleanedText = remainingText.replace(Regex("[^a-zA-Z0-9\\s]"), " ")
        val tokens =
            cleanedText
                .split(Regex("\\s+"))
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

        val keywords = tokens.filter { it !in stopWords }.distinct().take(6)

        var currentKeywords = keywords.toMutableList()
        var params =
            XSearchParams(
                keywords = currentKeywords.takeIf { it.isNotEmpty() },
                phrases = phrases.takeIf { it.isNotEmpty() },
                hashtags = hashtags.takeIf { it.isNotEmpty() },
                mentions = mentions.takeIf { it.isNotEmpty() },
            )

        val buildQuery = BuildXQueryUseCase()
        while (currentKeywords.isNotEmpty() && buildQuery(params).length >= 128) {
            currentKeywords.removeAt(currentKeywords.lastIndex)
            params = params.copy(keywords = currentKeywords.takeIf { it.isNotEmpty() })
        }

        val totalTermsSize =
            (params.keywords?.size ?: 0) +
                (params.phrases?.size ?: 0) +
                (params.hashtags?.size ?: 0) +
                (params.mentions?.size ?: 0)

        if (totalTermsSize == 0) {
            return DeriveXSearchParamsResult(params = null, skipReason = "No search terms extracted")
        }

        return DeriveXSearchParamsResult(params = params, skipReason = null)
    }
}
