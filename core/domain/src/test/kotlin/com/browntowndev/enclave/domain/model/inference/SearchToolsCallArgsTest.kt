package com.browntowndev.enclave.domain.model.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchToolsCallArgsTest {
    @Test
    fun parse_returns_defaults_for_invalid_json() {
        val args = SearchToolsCallArgs.parse("not-json")
        assertEquals("", args.query)
        assertEquals(ToolSearchThresholds.DEFAULT_SEARCH_LIMIT, args.limit)
    }

    @Test
    fun parse_coerces_non_primitive_query_to_empty() {
        val args = SearchToolsCallArgs.parse("""{"query":["nested"],"limit":"3"}""")
        assertEquals("", args.query)
        assertEquals(3, args.limit)
    }

    @Test
    fun parse_coerces_non_numeric_limit_to_default() {
        val args = SearchToolsCallArgs.parse("""{"query":"web search","limit":{"bad":true}}""")
        assertEquals("web search", args.query)
        assertEquals(ToolSearchThresholds.DEFAULT_SEARCH_LIMIT, args.limit)
    }

    @Test
    fun parse_accepts_valid_payload() {
        val args = SearchToolsCallArgs.parse("""{"query":"append blocks","limit":2}""")
        assertEquals("append blocks", args.query)
        assertEquals(2, args.limit)
    }

    @Test
    fun parse_reads_capability_key() {
        val args = SearchToolsCallArgs.parse("""{"capability":"send email","limit":3}""")
        assertEquals("send email", args.query)
        assertEquals(3, args.limit)
    }

    @Test
    fun parse_falls_back_to_query_when_capability_absent() {
        val args = SearchToolsCallArgs.parse("""{"query":"send email","limit":3}""")
        assertEquals("send email", args.query)
        assertEquals(3, args.limit)
    }
}