package com.browntowndev.enclave.domain.model.inference

object ProfileAnalysisTools {
    val LIST_CONVERSATIONS =
        ToolDefinition(
            name = "list_conversations",
            description =
                "List conversations in the analysis corpus with metadata (id, title, date, signal_score). " +
                    "Use this to get an overview and identify conversations worth reading in depth. " +
                    "Paginate with page/page_size. Filter with min_score.",
            parametersClass = ListConversationsParams::class,
            displayName = "Listing conversations...",
        )

    val READ_CONVERSATION =
        ToolDefinition(
            name = "read_conversation",
            description =
                "Read the messages of a specific conversation by ID. " +
                    "Returns 15 messages per page. Request subsequent pages if needed. " +
                    "Returns empty result for out-of-range pages.",
            parametersClass = ReadConversationParams::class,
            displayName = "Reading conversation...",
        )

    val SEARCH_CONVERSATIONS =
        ToolDefinition(
            name = "search_conversations",
            description =
                "Search across conversation titles and user messages for a keyword or phrase. " +
                    "Returns ranked snippets from matching conversations. " +
                    "Use this to locate evidence for a specific pattern or behaviour.",
            parametersClass = SearchConversationsParams::class,
            displayName = "Searching conversations...",
        )

    val RECORD_FINDING =
        ToolDefinition(
            name = "record_finding",
            description =
                "Record an intermediate finding during analysis. " +
                    "Categories: EVIDENCE (concrete behavioural moment), " +
                    "PATTERN (recurring theme), SYNTHESIS (profile section draft), " +
                    "CRITIQUE (self-review note).",
            parametersClass = RecordFindingParams::class,
            displayName = "Recording finding...",
        )

    val READ_FINDINGS =
        ToolDefinition(
            name = "read_findings",
            description =
                "Read all recorded findings, optionally filtered by category. " +
                    "Use this to review accumulated evidence before synthesis.",
            parametersClass = ReadFindingsParams::class,
            displayName = "Reading findings...",
        )

    val ALL_PROFILE_TOOLS =
        listOf(
            LIST_CONVERSATIONS,
            READ_CONVERSATION,
            SEARCH_CONVERSATIONS,
            RECORD_FINDING,
            READ_FINDINGS,
        )

    fun fromName(name: String): ToolDefinition? = ALL_PROFILE_TOOLS.find { it.name == name }
}
