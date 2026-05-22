package com.browntowndev.enclave.core.data.repository

import kotlinx.serialization.Serializable

@Serializable
data class MappedSearchResponse(
    val query: String,
    val results: List<SearchResult>,
)

@Serializable
data class SearchResult(
    val title: String,
    val url: String,
    val content: String,
    val score: Double,
)

@Serializable
data class MappedExtractResponse(
    val results: List<ExtractResult>,
)

@Serializable
data class ExtractResult(
    val url: String,
    val raw_content: String,
)
