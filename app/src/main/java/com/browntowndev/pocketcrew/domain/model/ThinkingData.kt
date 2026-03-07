package com.browntowndev.pocketcrew.domain.model

data class ThinkingData(
    val durationSeconds: Int,
    val steps: List<String>, // The ~10 word truncated snippets for the UI header
    val rawFullThought: String // The complete, untruncated chain-of-thought
)