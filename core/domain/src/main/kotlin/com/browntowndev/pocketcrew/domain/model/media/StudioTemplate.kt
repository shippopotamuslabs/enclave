package com.browntowndev.pocketcrew.domain.model.media

data class StudioTemplate(
    val id: String,
    val name: String,
    val exampleUri: String,
    val promptPrefix: String = "",
    val promptSuffix: String = ""
)
